package com.example.gymqrdisplayer.wear.tile

import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicColor
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import java.time.Instant
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.AngularLayoutConstraint
import androidx.wear.protolayout.DimensionBuilders.DegreesProp
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.gymqrdisplayer.wear.DataStoreManager
import com.example.gymqrdisplayer.wear.GymRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer

/**
 * GYM QR Tile
 *
 * - 每次滑到 / 點擊 Tile 都重新取得 QR（伺服器 QR 只有效約 30 秒）
 * - 版面：外圈倒數環 + 白色圓角卡片包住 QR（過期時底部顯示「已過期」）
 * - 倒數用多個 timeline entry 實現（不需背景網路），過期後切到「已過期」畫面
 */
class GymTileService : TileService() {

    private companion object {
        const val TAG = "GymTile"
        const val QR_VALID_SEC = 28
        const val FETCH_TIMEOUT_MS = 12_000L
        const val QR_IMAGE_ID = "qr_image"

        // 卡片尺寸相對螢幕寬度（192dp 螢幕 → 約 112dp 卡片），確保四角落在圓內
        const val CARD_RATIO = 0.6f
        const val CARD_PADDING_DP = 6f
        const val CARD_CORNER_DP = 16f
        const val RING_DP = 5f
        
        // Wear Material 預設色票（Colors.DEFAULT + Wear secondary/error）
        val C_PRIMARY = Colors.DEFAULT.primary
        val C_ON_SURFACE = Colors.DEFAULT.onSurface
        val C_ON_SURFACE_VARIANT = Colors.DEFAULT.onSurface
        val C_TRACK = Colors.DEFAULT.surface
        const val C_WARN = 0xFFFDE293.toInt()   // Wear Material secondary
        const val C_DANGER = 0xFFFD6B6B.toInt() // Wear Material error
        const val C_CARD = 0xFFFFFFFF.toInt()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        Log.d(TAG, "onTileRequest")
        serviceScope.launch {
            val tile = try {
                buildTileFresh(requestParams.deviceConfiguration)
            } catch (e: Exception) {
                Log.e(TAG, "Error building tile", e)
                singleEntryTile(
                    messageLayout(requestParams.deviceConfiguration, "更新失敗", "請檢查網路", "重試", reloadAction())
                )
            }
            future.set(tile)
        }
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val future = SettableFuture.create<ResourceBuilders.Resources>()
        serviceScope.launch { future.set(buildResources(readSavedQr())) }
        return future
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        Log.d(TAG, "onTileEnterEvent")
        getUpdater(this).requestUpdate(GymTileService::class.java)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- data

    private suspend fun buildTileFresh(device: DeviceParameters): TileBuilders.Tile {
        val store = DataStoreManager(applicationContext)
        val uid = store.uidFlow.first()
        val pwd = store.getPassword()
        if (uid.isNullOrBlank() || pwd.isNullOrBlank()) {
            return singleEntryTile(messageLayout(device, "尚未登入", "請在 App 設定帳號", "開啟 App", openAppAction()))
        }

        val qrContent = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            GymRepository.instance.fetchQrForUser(uid, pwd)
        } ?: return singleEntryTile(messageLayout(device, "更新失敗", "請檢查網路", "重試", reloadAction()))

        // onTileResourcesRequest 從 DataStore 讀這份內容產生圖片
        store.saveQrContent(qrContent)

        val now = System.currentTimeMillis()

        val expiresAt = now + QR_VALID_SEC * 1000L
        // 倒數由 platform time 動態運算（每秒更新）；過期後的 timeline entry 作為備援
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(entry(now, expiresAt, qrLayout(device, expiresAt)))
            .addTimelineEntry(
                entry(
                    expiresAt, Long.MAX_VALUE,
                    messageLayout(device, "已過期", null, "重新產生", reloadAction())
                )
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersionOf(qrContent))
            .setTileTimeline(timeline)
            .build()
    }

    private suspend fun readSavedQr(): String? = try {
        DataStoreManager(applicationContext).qrContentFlow.first()
    } catch (e: Exception) {
        Log.e(TAG, "Error reading QR content from DataStore", e)
        null
    }

    private fun resourcesVersionOf(qrContent: String?) =
        if (qrContent != null) "qr_${qrContent.hashCode()}" else "empty"

    private suspend fun buildResources(qrContent: String?): ResourceBuilders.Resources {
        val builder = ResourceBuilders.Resources.Builder().setVersion(resourcesVersionOf(qrContent))
        if (qrContent != null) {
            try {
                val bitmap = GymRepository.instance.createQRCodeBitmapForTile(qrContent)
                val buffer = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buffer)
                builder.addIdToImageMapping(
                    QR_IMAGE_ID,
                    ResourceBuilders.ImageResource.Builder()
                        .setInlineResource(
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(buffer.array())
                                .setWidthPx(bitmap.width)
                                .setHeightPx(bitmap.height)
                                .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                                .build()
                        )
                        .build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error building QR image resource", e)
            }
        }
        return builder.build()
    }

    // ---------------------------------------------------------------- layout

    private fun entry(start: Long, end: Long, root: LayoutElement) =
        TimelineBuilders.TimelineEntry.Builder()
            .setValidity(TimelineBuilders.TimeInterval.Builder().setStartMillis(start).setEndMillis(end).build())
            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
            .build()

    private fun singleEntryTile(root: LayoutElement) =
        TileBuilders.Tile.Builder()
            .setResourcesVersion("empty")
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()

    /** 距離過期的剩餘秒數（動態，每秒更新） */
    private fun remainingSec(expiresAt: Long): DynamicInt32 =
        DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(expiresAt)))
            .toIntSeconds()

    private fun dynamicAccent(remaining: DynamicInt32): DynamicColor =
        DynamicColor.onCondition(remaining.lte(6)).use(C_DANGER)
            .elseUse(DynamicColor.onCondition(remaining.lte(12)).use(C_WARN).elseUse(C_PRIMARY))

    /**
     * 外圈倒數：灰色軌道 + 彩色弧線。弧線長度是動態值、在 360° 版位內置中，
     * 所以會從頂端往兩側對稱縮短，最後收在底部。progress 為 null 時只畫軌道。
     */
    private fun ring(progress: ArcLine?): LayoutElement {
        val box = Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(3f)).build())
                    .build()
            )
            .addContent(
                arc(ArcLine.Builder().setLength(degrees(360f)).setThickness(dp(RING_DP)).setColor(argb(C_TRACK)).build())
            )
        if (progress != null) box.addContent(arc(progress))
        return box.build()
    }

    private fun countdownRing(remaining: DynamicInt32): LayoutElement {
        val sweep = DynamicFloat.onCondition(remaining.lte(0)).use(0f)
            .elseUse(remaining.asFloat().times(360f / QR_VALID_SEC))
        return ring(
            ArcLine.Builder()
                .setLength(DegreesProp.Builder(360f).setDynamicValue(sweep).build())
                .setLayoutConstraintsForDynamicLength(AngularLayoutConstraint.Builder(360f).build())
                .setThickness(dp(RING_DP))
                .setColor(ColorProp.Builder(C_PRIMARY).setDynamicValue(dynamicAccent(remaining)).build())
                .build()
        )
    }

    private fun arc(line: ArcLine) =
        Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(line)
            .build()

    private fun text(value: String, typography: Int, color: Int) =
        Text.Builder(this, value)
            .setTypography(typography)
            .setColor(argb(color))
            .build()

    /** 主畫面：外圈倒數 + 白卡 QR；點任何地方重新取得 */
    private fun qrLayout(device: DeviceParameters, expiresAt: Long): LayoutElement {
        val screen = device.screenWidthDp.takeIf { it > 0 } ?: 192
        val cardSize = screen * CARD_RATIO
        val qrSize = cardSize - CARD_PADDING_DP * 2
        val remaining = remainingSec(expiresAt)

        val card = Box.Builder()
            .setWidth(dp(cardSize))
            .setHeight(dp(cardSize))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(C_CARD))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(CARD_CORNER_DP)).build())
                            .build()
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder().setId("refresh").setOnClick(reloadAction()).build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(QR_IMAGE_ID)
                    .setWidth(dp(qrSize))
                    .setHeight(dp(qrSize))
                    .build()
            )
            .build()

        val content = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // 上方留出與底部狀態列等高的空間，讓 QR 置中
            .addContent(text(" ", Typography.TYPOGRAPHY_BUTTON, C_ON_SURFACE))
            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(card)
            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(statusText(remaining))
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // 整個 tile 都可點擊重新產生（enter 事件在新版 Wear OS 不保證送達，無法可靠地自動更新）
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder().setId("refresh_all").setOnClick(reloadAction()).build()
                    )
                    .build()
            )
            .addContent(countdownRing(remaining))
            .addContent(content)
            .build()
    }

    /** 底部狀態：倒數期間留白（外圈就是指示），過期後顯示「已過期」 */
    private fun statusText(remaining: DynamicInt32): LayoutElement {
        val dyn = DynamicString.onCondition(remaining.lte(0)).use("已過期").elseUse(" ")
        return Text.Builder(
            this,
            StringProp.Builder(" ").setDynamicValue(dyn).build(),
            StringLayoutConstraint.Builder("已過期").build()
        )
            .setTypography(Typography.TYPOGRAPHY_BUTTON)
            .setColor(argb(C_DANGER))
            .build()
    }

    /** 狀態畫面（過期 / 失敗 / 未登入）：灰色外圈 + 標題 + 說明 + Material CompactChip */
    private fun messageLayout(
        device: DeviceParameters,
        title: String,
        subtitle: String?,
        chipText: String,
        action: ActionBuilders.Action
    ): LayoutElement {
        val content = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(title, Typography.TYPOGRAPHY_TITLE1, C_ON_SURFACE))
            .apply {
                if (subtitle != null) {
                    addContent(Spacer.Builder().setHeight(dp(2f)).build())
                    addContent(text(subtitle, Typography.TYPOGRAPHY_CAPTION1, C_ON_SURFACE_VARIANT))
                }
            }
            .addContent(Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(
                CompactChip.Builder(
                    this,
                    chipText,
                    ModifiersBuilders.Clickable.Builder().setId("action").setOnClick(action).build(),
                    device
                ).build()
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(ring(null))
            .addContent(content)
            .build()
    }

    private fun reloadAction(): ActionBuilders.Action = ActionBuilders.LoadAction.Builder().build()

    private fun openAppAction(): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName("com.example.gymqrdisplayer")
                    .setClassName("com.example.gymqrdisplayer.wear.MainActivity")
                    .build()
            )
            .build()
}

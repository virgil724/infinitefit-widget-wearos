package com.example.gymqrdisplayer.wear.tile

import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.gymqrdisplayer.wear.DataStoreManager
import com.example.gymqrdisplayer.wear.GymRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class GymTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        serviceScope.launch {
            val qrContent = readQrContent()
            future.set(buildTile(qrContent))
        }
        return future
    }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val future = SettableFuture.create<ResourceBuilders.Resources>()
        serviceScope.launch {
            val qrContent = readQrContent()
            future.set(buildResources(qrContent))
        }
        return future
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        getUpdater(this).requestUpdate(GymTileService::class.java)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun readQrContent(): String? = try {
        DataStoreManager(applicationContext).qrContentFlow.first()
    } catch (e: Exception) {
        Log.e("GymTile", "Error reading QR content from DataStore", e)
        null
    }

    private fun buildTile(qrContent: String?): TileBuilders.Tile {
        val resourcesVersion = if (qrContent != null) "qr_${qrContent.hashCode()}" else "empty"
        val layout = if (qrContent != null) buildQrLayout() else buildNoQrLayout()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersion)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private suspend fun buildResources(qrContent: String?): ResourceBuilders.Resources {
        val resourcesVersion = if (qrContent != null) "qr_${qrContent.hashCode()}" else "empty"
        val builder = ResourceBuilders.Resources.Builder().setVersion(resourcesVersion)

        if (qrContent != null) {
            try {
                val bitmap = GymRepository.instance.createQRCodeBitmapForTile(qrContent)
                val buffer = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buffer)
                builder.addIdToImageMapping(
                    "qr_image",
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
                Log.e("GymTile", "Error building QR image resource", e)
            }
        }

        return builder.build()
    }

    private fun buildQrLayout(): LayoutElementBuilders.LayoutElement {
        val openAppAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName("com.example.gymqrdisplayer")
                    .setClassName("com.example.gymqrdisplayer.wear.MainActivity")
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(openAppAction)
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId("qr_image")
                    .setWidth(dp(180f))
                    .setHeight(dp(180f))
                    .build()
            )
            .build()
    }

    private fun buildNoQrLayout(): LayoutElementBuilders.LayoutElement {
        val openAppAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName("com.example.gymqrdisplayer")
                    .setClassName("com.example.gymqrdisplayer.wear.MainActivity")
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("GYM QR")
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(dp(8f))
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("點擊開啟 App")
                            .setModifiers(
                                ModifiersBuilders.Modifiers.Builder()
                                    .setClickable(
                                        ModifiersBuilders.Clickable.Builder()
                                            .setOnClick(openAppAction)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

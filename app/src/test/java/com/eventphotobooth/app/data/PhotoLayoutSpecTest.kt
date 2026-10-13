package com.eventphotobooth.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLayoutSpecTest {
    @Test
    fun onePhotoUsesPortraitGuidanceWhileThreePhotosRemainLandscape() {
        assertEquals(4f / 5f, PhotoLayoutSpecs.onePhoto.photoAspectRatio, 0.0001f)
        assertEquals(16f / 9f, PhotoLayoutSpecs.threePhoto.photoAspectRatio, 0.0001f)
        assertTrue(PhotoLayoutSpecs.onePhoto.dimOutsideCaptureFrame)
        assertTrue(!PhotoLayoutSpecs.threePhoto.dimOutsideCaptureFrame)
    }

    @Test
    fun fourAndSixPhotoLayoutsUsePortraitWindowsAndDimmedGuidance() {
        assertEquals(4, PhotoLayoutSpecs.fourPhoto.photoRegions(40f, 220f, 496f, 620f).size)
        assertEquals(6, PhotoLayoutSpecs.sixPhoto.photoRegions(40f, 220f, 496f, 900f).size)
        assertTrue(PhotoLayoutSpecs.fourPhoto.dimOutsideCaptureFrame)
        assertTrue(PhotoLayoutSpecs.sixPhoto.dimOutsideCaptureFrame)
    }

    @Test
    fun soloPhotoUsesThePortraitArtworkWindow() {
        val spec = PhotoLayoutSpecs.onePhoto
        val region = spec.photoRegions(
            spec.artworkPhotoLeft,
            spec.artworkPhotoTop,
            spec.artworkPhotoWidth,
            spec.artworkPhotoHeight,
        ).single()
        assertEquals(4f / 5f, region.width / region.height, 0.001f)
        assertEquals(18f, region.x, 0.001f)
        assertEquals(320f, region.y, 0.001f)
        assertEquals(540f, region.width, 0.001f)
        assertEquals(675f, region.height, 0.001f)
    }

    @Test
    fun everyLayoutKeepsItsDeclaredPhotoWindowRatio() {
        val areas = mapOf(
            LayoutId.ONE_PHOTO to PhotoLayoutSpecs.onePhoto.photoRegions(18f, 320f, 540f, 675f),
            LayoutId.THREE_STRIP to PhotoLayoutSpecs.threePhoto.photoRegions(18f, 320f, 540f, 936f),
            LayoutId.FOUR_GRID to PhotoLayoutSpecs.fourPhoto.photoRegions(18f, 320f, 540f, 672f),
            LayoutId.SIX_GRID to PhotoLayoutSpecs.sixPhoto.photoRegions(18f, 320f, 540f, 1014f),
        )
        areas.forEach { (layout, regions) ->
            regions.forEach { region ->
                assertEquals(
                    PhotoLayoutSpecs.forLayout(layout).photoAspectRatio,
                    region.width / region.height,
                    0.02f,
                )
            }
        }
    }

    @Test
    fun artworkTemplatesKeepTheirLayoutCanvasHeight() {
        LayoutId.entries.forEach { layout ->
            val template = ResponsiveBorderTemplate(
                id = "${layout.name}-template",
                name = layout.displayName,
                source = ReceiptBorderSource.BUNDLED,
                layoutId = layout,
                normalizedAssetPath = "template.png",
            )

            val document = template.asReceiptDocument(ReceiptTemplate()).document

            assertEquals(PhotoLayoutSpecs.forLayout(layout).defaultCanvasHeight, document?.designHeight ?: 0f, 0.001f)
        }
    }

    @Test
    fun stickersAreClampedInsideTheSameReceiptSafeAreaForEveryLayout() {
        LayoutId.entries.forEach { layoutId ->
            val spec = PhotoLayoutSpecs.forLayout(layoutId)
            val clamped = spec.stickerSafeArea.clamp(
                StickerPlacement(StickerId.HEART, x = -20f, y = spec.defaultCanvasHeight + 20f, scale = 1f),
            )

            assertTrue(clamped.x >= spec.stickerSafeArea.left)
            assertTrue(clamped.x <= spec.stickerSafeArea.right)
            assertTrue(clamped.y >= spec.stickerSafeArea.top)
            assertTrue(clamped.y <= spec.stickerSafeArea.bottom)
            assertEquals(0f, spec.stickerSafeArea.top, 0.001f)
            assertEquals(spec.defaultCanvasHeight, spec.stickerSafeArea.bottom, 0.001f)
        }
    }

    @Test
    fun everyArtworkGuideReservesTwoTopOperatorAreasWithoutShrinkingPhotos() {
        LayoutId.entries.forEach { layoutId ->
            val spec = PhotoLayoutSpecs.forLayout(layoutId)
            assertEquals(2, spec.operatorTopAreaCount)
            assertEquals(320f, spec.artworkPhotoTop, 0.001f)
            assertTrue(spec.defaultCanvasHeight > spec.artworkPhotoTop + spec.artworkPhotoHeight)
        }
    }
}

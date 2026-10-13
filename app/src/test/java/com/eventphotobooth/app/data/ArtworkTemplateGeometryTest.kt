package com.eventphotobooth.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkTemplateGeometryTest {
    @Test
    fun artworkTemplatesUseTheCompleteAuthoredPhotoSlot() {
        val templates = listOf(
            LayoutId.ONE_PHOTO to "receipt_templates/builtin_1_test_moments.jpg",
            LayoutId.ONE_PHOTO to "receipt_templates/builtin_1_capture_your_story_today.jpg",
            LayoutId.ONE_PHOTO to "receipt_templates/builtin_1_no_bad_days.jpg",
            LayoutId.THREE_STRIP to "receipt_templates/builtin_3_test_moments.jpg",
            LayoutId.THREE_STRIP to "receipt_templates/builtin_3_capture_your_story_today.jpg",
            LayoutId.THREE_STRIP to "receipt_templates/builtin_3_no_bad_days.jpg",
            LayoutId.THREE_STRIP to "receipt_templates/builtin_3_plane_ticket.jpg",
            LayoutId.FOUR_GRID to "receipt_templates/builtin_4_test_moments.jpg",
            LayoutId.FOUR_GRID to "receipt_templates/builtin_4_capture_your_story_today.jpg",
            LayoutId.FOUR_GRID to "receipt_templates/builtin_4_no_bad_days.jpg",
            LayoutId.FOUR_GRID to "receipt_templates/builtin_4_plane_ticket.jpg",
            LayoutId.SIX_GRID to "receipt_templates/builtin_6_test_moments.jpg",
            LayoutId.SIX_GRID to "receipt_templates/builtin_6_capture_your_story_today.jpg",
            LayoutId.SIX_GRID to "receipt_templates/builtin_6_no_bad_days.jpg",
            LayoutId.SIX_GRID to "receipt_templates/builtin_6_plane_ticket.jpg",
        )

        templates.forEach { (layoutId, artworkPath) ->
            val document = ResponsiveBorderTemplate(
                id = "artwork-$layoutId",
                name = "Artwork $layoutId",
                layoutId = layoutId,
                source = ReceiptBorderSource.BUNDLED,
                normalizedAssetPath = artworkPath,
            ).asReceiptDocument(ReceiptTemplate()).document!!

            val regions = document.photoRegions(layoutId)
            assertEquals(0f, document.photoSideInset, EPSILON)
            val spec = PhotoLayoutSpecs.forLayout(layoutId)
            assertEquals(spec.artworkPhotoLeft, regions.minOf { it.x }, EPSILON)
            assertEquals(spec.artworkPhotoLeft + spec.artworkPhotoWidth, regions.maxOf { it.x + it.width }, EPSILON)

            regions.forEach { region ->
                assertEquals(
                    PhotoLayoutSpecs.forLayout(layoutId).photoAspectRatio,
                    region.width / region.height,
                    EPSILON,
                )
            }
        }
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}

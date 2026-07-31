package com.darius.unison.playback

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnisonMediaArtworkTest {
    @Test
    fun fixedArtworkHasDarkBackgroundAndWhiteBrandMark() {
        val encoded = UnisonMediaArtwork.createPng()
        val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)

        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)

        val background = bitmap.getPixel(0, 0)
        assertTrue(Color.red(background) < 40)
        assertTrue(Color.green(background) < 40)
        assertTrue(Color.blue(background) < 40)

        val logo = bitmap.getPixel(128, 128)
        assertTrue(Color.red(logo) > 240)
        assertTrue(Color.green(logo) > 240)
        assertTrue(Color.blue(logo) > 240)
        bitmap.recycle()
    }
}

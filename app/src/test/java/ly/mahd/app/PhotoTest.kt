package ly.mahd.app

import ly.mahd.app.data.GrowthEntry
import ly.mahd.app.data.PhotoStore
import ly.mahd.app.data.Profile
import ly.mahd.app.data.ProfileKind
import ly.mahd.app.data.Sex
import ly.mahd.app.logic.PhotoRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhotoRulesTest {
    private val birth = LocalDate.of(2026, 1, 15)
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun readsTheDayFromExifDates() {
        assertEquals(LocalDate.of(2026, 3, 1), PhotoRules.dateFromExif("2026:03:01 10:15:00"))
        assertEquals(LocalDate.of(2026, 3, 1), PhotoRules.dateFromExif(" 2026:03:01"))
    }

    @Test
    fun ignoresMissingOrBrokenExifDates() {
        assertNull(PhotoRules.dateFromExif(null))
        assertNull(PhotoRules.dateFromExif(""))
        assertNull(PhotoRules.dateFromExif("0000:00:00 00:00:00")) // what some cameras write when they don't know
        assertNull(PhotoRules.dateFromExif("2026:13:40 10:00:00"))
        assertNull(PhotoRules.dateFromExif("yesterday"))
    }

    @Test
    fun keepsPhotoDatesBetweenBirthAndToday() {
        assertEquals(birth, PhotoRules.clampDate(LocalDate.of(2020, 1, 1), birth, today))
        assertEquals(today, PhotoRules.clampDate(LocalDate.of(2030, 1, 1), birth, today))
        assertEquals(LocalDate.of(2026, 5, 5), PhotoRules.clampDate(LocalDate.of(2026, 5, 5), birth, today))
    }

    @Test
    fun showsTheWeightMeasuredCloseToThePhoto() {
        val entries = listOf(
            GrowthEntry(1, 1, LocalDate.of(2026, 3, 1), 5.0, null, null),
            GrowthEntry(2, 1, LocalDate.of(2026, 3, 20), 5.6, null, null),
            GrowthEntry(3, 1, LocalDate.of(2026, 3, 25), null, 60.0, null), // no weight
        )
        assertEquals(5.6, PhotoRules.weightNear(entries, LocalDate.of(2026, 3, 18))!!, 1e-9)
        assertEquals(5.0, PhotoRules.weightNear(entries, LocalDate.of(2026, 2, 20))!!, 1e-9)
        assertNull(PhotoRules.weightNear(entries, LocalDate.of(2026, 6, 1))) // nothing within 14 days
    }

    @Test
    fun comparesThePreferredOrOldestPhotoWithTheNewest() {
        assertEquals(1 to 4, PhotoRules.defaultPair(listOf(1, 2, 3, 4), null))
        assertEquals(2 to 4, PhotoRules.defaultPair(listOf(1, 2, 3, 4), 2))
        assertEquals(4 to 3, PhotoRules.defaultPair(listOf(1, 2, 3, 4), 4))
        assertEquals(1 to 4, PhotoRules.defaultPair(listOf(1, 2, 3, 4), 9)) // deleted meanwhile
        assertNull(PhotoRules.defaultPair(listOf(1), null))
    }
}

class PhotoStoreTest {
    @Test
    fun decodesBigPhotosAtAReducedSizeButNeverBelowTheTarget() {
        assertEquals(1, PhotoStore.sampleSize(1600, 1600))
        assertEquals(2, PhotoStore.sampleSize(4000, 1600)) // 2000 px, then scaled to 1600
        assertEquals(8, PhotoStore.sampleSize(4000, 400)) // 500 px is still at least 400
        assertEquals(4, PhotoStore.sampleSize(4000, 600)) // 1000 px; 1/8 would give only 500
        assertEquals(1, PhotoStore.sampleSize(300, 400)) // small pictures stay as they are
    }
}

class RestoreKeepsPhotosTest {
    private val child = Profile(1, ProfileKind.CHILD, "سارة", Sex.GIRL, LocalDate.of(2025, 6, 1), null, null)

    @Test
    fun theSameChildInABackupKeepsItsPhotos() {
        assertTrue(child.isSameChildAs(child.copy(weeksAtBirth = 38))) // other details may have changed
    }

    @Test
    fun anotherChildWithTheSameIdDoesNotGetThem() {
        assertFalse(child.isSameChildAs(child.copy(name = "عمر")))
        assertFalse(child.isSameChildAs(child.copy(birthDate = LocalDate.of(2026, 8, 7))))
        assertFalse(child.isSameChildAs(child.copy(id = 2)))
    }
}

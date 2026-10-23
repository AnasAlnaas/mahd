package ly.mahd.app.data

import java.time.LocalDate

/** A profile is either an ongoing pregnancy or a child that has been born. */
enum class ProfileKind(val code: String) {
    PREGNANCY("pregnancy"),
    CHILD("child");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: CHILD
    }
}

enum class Sex(val code: String) {
    BOY("boy"),
    GIRL("girl");

    /** 1 = boy, 2 = girl, as in the WHO growth tables. */
    val whoCode: Int get() = if (this == BOY) 1 else 2

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/** How the baby was born, asked when "my baby is born" is pressed (for the mother's الأربعين). */
enum class BirthType(val code: String) {
    NATURAL("natural"),
    CAESAREAN("caesarean");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}

data class Profile(
    val id: Long,
    val kind: ProfileKind,
    val name: String,
    val sex: Sex?,
    /** Set for children. */
    val birthDate: LocalDate?,
    /** Set for pregnancies; kept after birth so we know how early or late the baby came. */
    val dueDate: LocalDate?,
    /** Completed weeks of pregnancy at birth. Null when unknown or born at term. */
    val weeksAtBirth: Int?,
    /** Natural birth or caesarean; null when not told (children added before this was asked). */
    val birthType: BirthType? = null,
) {
    val isPregnancy get() = kind == ProfileKind.PREGNANCY
    val isChild get() = kind == ProfileKind.CHILD && birthDate != null

    /** Babies born before 37 weeks use "corrected age" for milestones and growth until age 2. */
    val isPremature get() = weeksAtBirth != null && weeksAtBirth < 37

    /**
     * True when [other] describes the same child: same id, kind, name and birth date. Backup files hold no
     * photos, so after a restore a child keeps its photos only if the backup describes it the same way.
     */
    fun isSameChildAs(other: Profile): Boolean =
        id == other.id && kind == other.kind && name == other.name && birthDate == other.birthDate
}

/** A photo of a child. The picture itself is a file in the app's private folder (see [PhotoStore]). */
data class Photo(
    val id: Long,
    val profileId: Long,
    /** The day the photo was taken (the child's age is worked out from it). */
    val date: LocalDate,
    /** File name inside the photo folder. */
    val file: String,
)

data class GrowthEntry(
    val id: Long,
    val profileId: Long,
    val date: LocalDate,
    val weightKg: Double?,
    val lengthCm: Double?,
    val headCm: Double?,
)

data class DoseRecord(
    val profileId: Long,
    val doseId: String,
    val givenDate: LocalDate,
)

/** One exercise of the daily program, ticked as done on [date]. */
data class ExerciseTick(
    val profileId: Long,
    val date: LocalDate,
    val exerciseId: String,
)

/** Everything recorded for one child, read in one go so a screen gets it all at the same moment. */
data class ChildRecords(
    /** Dose id -> date given. */
    val doses: Map<String, LocalDate>,
    /** Doses marked "skipped / not applicable". */
    val skipped: Set<String>,
    /** Milestone item id -> answer. */
    val answers: Map<String, MilestoneAnswer>,
    /** Measurements, oldest first. */
    val growth: List<GrowthEntry>,
)

enum class MilestoneAnswer(val code: Int) {
    YES(1),
    NOT_YET(0),
    /** "يحاول": the child is starting to do it. Same code as the old "not sure" answer, so old saves and backups still load. */
    PROGRESS(2);

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

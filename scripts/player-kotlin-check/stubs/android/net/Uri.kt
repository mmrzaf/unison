package android.net

import java.io.File

class Uri private constructor() {
    companion object {
        fun fromFile(file: File): Uri = Uri()
    }
}

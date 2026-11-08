package com.example.data

import com.example.model.FileCategory
import com.example.model.SharedFile

object SampleDataProvider {

    val sampleFiles: List<SharedFile> = listOf(
        // Images
        SharedFile(
            id = "img_1",
            name = "IMG_20261106_NightSky.jpg",
            sizeBytes = 4_404_019L,
            formattedSize = "4.2 MB",
            category = FileCategory.IMAGE,
            extension = "JPG",
            lastModified = "Today, 18:22"
        ),
        SharedFile(
            id = "img_2",
            name = "Tokyo_Neon_Ultra4K.png",
            sizeBytes = 9_122_611L,
            formattedSize = "8.7 MB",
            category = FileCategory.IMAGE,
            extension = "PNG",
            lastModified = "Today, 14:05"
        ),
        SharedFile(
            id = "img_3",
            name = "Studio_Portrait_Raw04.dng",
            sizeBytes = 25_270_681L,
            formattedSize = "24.1 MB",
            category = FileCategory.IMAGE,
            extension = "DNG",
            lastModified = "Yesterday"
        ),
        SharedFile(
            id = "img_4",
            name = "Sunset_Horizon_HDR.webp",
            sizeBytes = 3_145_728L,
            formattedSize = "3.0 MB",
            category = FileCategory.IMAGE,
            extension = "WEBP",
            lastModified = "Nov 04"
        ),

        // Videos
        SharedFile(
            id = "vid_1",
            name = "Drone_Cinematic_4K_Reel.mp4",
            sizeBytes = 432_013_312L,
            formattedSize = "412 MB",
            category = FileCategory.VIDEO,
            extension = "MP4",
            lastModified = "Today, 16:40"
        ),
        SharedFile(
            id = "vid_2",
            name = "Vlog_Tokyo_Travel_Ep3.mov",
            sizeBytes = 298_844_160L,
            formattedSize = "285 MB",
            category = FileCategory.VIDEO,
            extension = "MOV",
            lastModified = "Yesterday"
        ),
        SharedFile(
            id = "vid_3",
            name = "App_Feature_Walkthrough.mp4",
            sizeBytes = 67_108_864L,
            formattedSize = "64 MB",
            category = FileCategory.VIDEO,
            extension = "MP4",
            lastModified = "Nov 03"
        ),

        // Audio
        SharedFile(
            id = "aud_1",
            name = "LoFi_Chill_Beats_Master.flac",
            sizeBytes = 39_845_888L,
            formattedSize = "38 MB",
            category = FileCategory.AUDIO,
            extension = "FLAC",
            lastModified = "Today, 11:15"
        ),
        SharedFile(
            id = "aud_2",
            name = "Tech_Podcast_Episode_44.mp3",
            sizeBytes = 47_185_920L,
            formattedSize = "45 MB",
            category = FileCategory.AUDIO,
            extension = "MP3",
            lastModified = "Nov 05"
        ),
        SharedFile(
            id = "aud_3",
            name = "Voice_Meeting_Notes_09.m4a",
            sizeBytes = 5_452_595L,
            formattedSize = "5.2 MB",
            category = FileCategory.AUDIO,
            extension = "M4A",
            lastModified = "Nov 02"
        ),

        // App
        SharedFile(
            id = "app_1",
            name = "QuickShare_Pro_v2.4.apk",
            sizeBytes = 34_393_292L,
            formattedSize = "32.8 MB",
            category = FileCategory.APP,
            extension = "APK",
            lastModified = "Today, 09:10"
        ),
        SharedFile(
            id = "app_2",
            name = "Retro_Arcade_Classic.apk",
            sizeBytes = 92_694_937L,
            formattedSize = "88.4 MB",
            category = FileCategory.APP,
            extension = "APK",
            lastModified = "Yesterday"
        ),
        SharedFile(
            id = "app_3",
            name = "Fast_Code_Editor.apk",
            sizeBytes = 14_889_779L,
            formattedSize = "14.2 MB",
            category = FileCategory.APP,
            extension = "APK",
            lastModified = "Nov 01"
        ),

        // Documents
        SharedFile(
            id = "doc_1",
            name = "Quarterly_Financial_Report.pdf",
            sizeBytes = 13_002_342L,
            formattedSize = "12.4 MB",
            category = FileCategory.DOCUMENTS,
            extension = "PDF",
            lastModified = "Today, 15:30"
        ),
        SharedFile(
            id = "doc_2",
            name = "System_Architecture_Blueprint.docx",
            sizeBytes = 7_130_316L,
            formattedSize = "6.8 MB",
            category = FileCategory.DOCUMENTS,
            extension = "DOCX",
            lastModified = "Yesterday"
        ),
        SharedFile(
            id = "doc_3",
            name = "Product_Pitch_Deck_Final.pptx",
            sizeBytes = 30_932_992L,
            formattedSize = "29.5 MB",
            category = FileCategory.DOCUMENTS,
            extension = "PPTX",
            lastModified = "Nov 04"
        ),

        // Archives
        SharedFile(
            id = "arc_1",
            name = "Full_SourceCode_Backup.zip",
            sizeBytes = 190_840_832L,
            formattedSize = "182 MB",
            category = FileCategory.ARCHIVES,
            extension = "ZIP",
            lastModified = "Today, 08:20"
        ),
        SharedFile(
            id = "arc_2",
            name = "Vector_Assets_Bundle_HD.tar.gz",
            sizeBytes = 356_515_840L,
            formattedSize = "340 MB",
            category = FileCategory.ARCHIVES,
            extension = "TAR.GZ",
            lastModified = "Nov 03"
        ),
        SharedFile(
            id = "arc_3",
            name = "Offline_Database_Dump.7z",
            sizeBytes = 545_259_520L,
            formattedSize = "520 MB",
            category = FileCategory.ARCHIVES,
            extension = "7Z",
            lastModified = "Oct 28"
        ),

        // Storage / Folder files
        SharedFile(
            id = "stor_1",
            name = "DCIM_Camera_Latest.zip",
            sizeBytes = 145_752_064L,
            formattedSize = "139 MB",
            category = FileCategory.STORAGE,
            extension = "ZIP",
            lastModified = "Internal / DCIM"
        ),
        SharedFile(
            id = "stor_2",
            name = "Downloads_ProjectFiles.bin",
            sizeBytes = 85_983_232L,
            formattedSize = "82 MB",
            category = FileCategory.STORAGE,
            extension = "BIN",
            lastModified = "Internal / Download"
        ),
        SharedFile(
            id = "stor_3",
            name = "Documents_Confidential_Log.csv",
            sizeBytes = 2_411_724L,
            formattedSize = "2.3 MB",
            category = FileCategory.STORAGE,
            extension = "CSV",
            lastModified = "Internal / Documents"
        )
    )
}

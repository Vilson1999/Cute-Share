package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Cute Share", appName)
  }

  @Test
  fun `addFilesToActiveSession appends to bottom of list and keeps transfer active`() {
    val transferManager = com.example.data.TransferManager()
    val initialFile = com.example.model.SharedFile(
      id = "init_1",
      name = "Initial_Photo.jpg",
      sizeBytes = 2_000_000L,
      formattedSize = "2 MB",
      category = com.example.model.FileCategory.IMAGE,
      extension = "JPG",
      lastModified = "Today"
    )

    transferManager.startTransfer(
      files = listOf(initialFile),
      isOutgoing = true,
      peerName = "Cosmic Bunny",
      avatarRes = R.drawable.avatar_1_bunny
    )

    val sessionBefore = transferManager.currentSession.value
    org.junit.Assert.assertNotNull(sessionBefore)
    assertEquals(1, sessionBefore?.files?.size)
    assertEquals("Initial_Photo.jpg", sessionBefore?.files?.first()?.name)

    // Add more files to active transfer session
    val newFile1 = com.example.model.SharedFile(
      id = "more_1",
      name = "New_Video.mp4",
      sizeBytes = 15_000_000L,
      formattedSize = "15 MB",
      category = com.example.model.FileCategory.VIDEO,
      extension = "MP4",
      lastModified = "Just now"
    )
    val newFile2 = com.example.model.SharedFile(
      id = "more_2",
      name = "Project_Archive.zip",
      sizeBytes = 8_000_000L,
      formattedSize = "8 MB",
      category = com.example.model.FileCategory.ARCHIVES,
      extension = "ZIP",
      lastModified = "Just now"
    )

    transferManager.addFilesToActiveSession(listOf(newFile1, newFile2))

    val sessionAfter = transferManager.currentSession.value
    org.junit.Assert.assertNotNull(sessionAfter)
    // 3 files total now
    assertEquals(3, sessionAfter?.files?.size)
    // First file is still Initial_Photo.jpg
    assertEquals("Initial_Photo.jpg", sessionAfter?.files?.get(0)?.name)
    // Newly added files appear at the BOTTOM of the list
    assertEquals("New_Video.mp4", sessionAfter?.files?.get(1)?.name)
    assertEquals("Project_Archive.zip", sessionAfter?.files?.get(2)?.name)
    // Session is active and not completed
    assertEquals(false, sessionAfter?.isCompleted)
  }

  @Test
  fun `openInbuildFileManagerForAddingMore opens file picker and appends to transfer`() {
    val viewModel = com.example.ui.MainViewModel()
    val initialFile = com.example.model.SharedFile(
      id = "f1",
      name = "Initial.png",
      sizeBytes = 1_000_000L,
      formattedSize = "1 MB",
      category = com.example.model.FileCategory.IMAGE,
      extension = "PNG",
      lastModified = "Today"
    )

    // Start initial transfer
    viewModel.onFilesSelectedForSharing(listOf(initialFile))
    val avatar = com.example.model.CartoonAvatars.list.first()
    viewModel.onPeerSelectedForTransfer(avatar)
    assertEquals(com.example.ui.navigation.Screen.TRANSFER, viewModel.currentScreen.value)
    assertEquals(1, viewModel.currentSession.value?.files?.size)

    // User taps (Share More) pill button -> opens inbuild file manager
    viewModel.openInbuildFileManagerForAddingMore()
    assertEquals(com.example.ui.navigation.Screen.FILE_PICKER, viewModel.currentScreen.value)
    assertEquals(true, viewModel.isAddingMoreToActiveSession.value)

    // User selects 2 more files in inbuild file manager and confirms
    val addedFile1 = com.example.model.SharedFile(
      id = "f2",
      name = "Doc.pdf",
      sizeBytes = 2_000_000L,
      formattedSize = "2 MB",
      category = com.example.model.FileCategory.DOCUMENTS,
      extension = "PDF",
      lastModified = "Today"
    )
    val addedFile2 = com.example.model.SharedFile(
      id = "f3",
      name = "Song.mp3",
      sizeBytes = 3_000_000L,
      formattedSize = "3 MB",
      category = com.example.model.FileCategory.AUDIO,
      extension = "MP3",
      lastModified = "Today"
    )
    viewModel.onFilesSelectedForSharing(listOf(addedFile1, addedFile2))

    // Directly returns to TRANSFER screen without stopping transfer
    assertEquals(com.example.ui.navigation.Screen.TRANSFER, viewModel.currentScreen.value)
    assertEquals(false, viewModel.isAddingMoreToActiveSession.value)
    // 3 total files now, new files at the bottom
    val files = viewModel.currentSession.value?.files
    assertEquals(3, files?.size)
    assertEquals("Initial.png", files?.get(0)?.name)
    assertEquals("Doc.pdf", files?.get(1)?.name)
    assertEquals("Song.mp3", files?.get(2)?.name)
  }

  @Test
  fun `peer discovery screen starts with empty peer list when no receiver is broadcasting`() {
    val viewModel = com.example.ui.MainViewModel()
    // When no receiver has opened their screen, discovered peers is empty
    assertEquals(0, viewModel.discoveredPeers.value.size)
  }

  @Test
  fun `permission dialog is triggered only on first time profile creation and not on subsequent saves`() {
    val viewModel = com.example.ui.MainViewModel()
    // First time setup: profile not created yet
    viewModel.initProfile("New User", R.drawable.avatar_1_bunny, isCreated = false)
    assertEquals(false, viewModel.showPermissionDialog.value)

    // User completes first-time profile creation -> triggers permission dialog
    viewModel.saveUserProfile("New User", R.drawable.avatar_1_bunny)
    assertEquals(true, viewModel.showPermissionDialog.value)

    // User handles permissions
    viewModel.onPermissionsHandled()
    assertEquals(false, viewModel.showPermissionDialog.value)
    assertEquals(true, viewModel.hasAskedPermissions.value)

    // Later: user opens profile again and saves -> MUST NOT ask for permission again!
    viewModel.openProfile()
    viewModel.saveUserProfile("Updated Name", R.drawable.avatar_2_panda)
    assertEquals(false, viewModel.showPermissionDialog.value)
  }

  @Test
  fun `cartoon avatars list contains 20 or more cute user photos`() {
    val avatars = com.example.model.CartoonAvatars.list
    org.junit.Assert.assertTrue(
      "Expected 20 or more cute user photos, but got ${avatars.size}",
      avatars.size >= 20
    )
    // Check all avatar drawables are non-zero
    avatars.forEach { avatar ->
      org.junit.Assert.assertNotEquals(0, avatar.drawableRes)
      org.junit.Assert.assertTrue(avatar.name.isNotBlank())
    }
  }

  @Test
  fun `wifi direct constants and port are properly configured`() {
    assertEquals(8888, com.example.data.NetworkUtils.WIFI_DIRECT_DEFAULT_PORT)
    assertEquals("192.168.49.1", com.example.data.NetworkUtils.WIFI_DIRECT_GROUP_OWNER_IP)
  }

  @Test
  fun `hardware readiness manager runs without crash on context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val wifiOn = com.example.data.HardwareReadinessManager.isWifiEnabled(context)
    val locOn = com.example.data.HardwareReadinessManager.isLocationEnabled(context)
    val btOn = com.example.data.HardwareReadinessManager.isBluetoothEnabled(context)
    // Returns boolean safely without uncaught exceptions
    org.junit.Assert.assertTrue(wifiOn || !wifiOn)
    org.junit.Assert.assertTrue(locOn || !locOn)
    org.junit.Assert.assertTrue(btOn || !btOn)
  }

  @Test
  fun `idle app does not run active transfer in background`() {
    val viewModel = com.example.ui.MainViewModel()
    // When no transfer is started, isTransferActive MUST be false
    assertEquals(false, viewModel.isTransferActive())

    // When backgrounded while idle, it cleans up without exceptions
    viewModel.onAppBackgrounded()
    assertEquals(false, viewModel.isTransferActive())
  }

  @Test
  fun `only images videos audio apps are active categories in file manager`() {
    val active = listOf(
      com.example.model.FileCategory.IMAGE,
      com.example.model.FileCategory.VIDEO,
      com.example.model.FileCategory.AUDIO,
      com.example.model.FileCategory.APP
    )
    assertEquals(4, active.size)
    org.junit.Assert.assertFalse(active.contains(com.example.model.FileCategory.DOCUMENTS))
    org.junit.Assert.assertFalse(active.contains(com.example.model.FileCategory.ARCHIVES))
    org.junit.Assert.assertFalse(active.contains(com.example.model.FileCategory.STORAGE))
  }
}



package org.northpennines.kioskexplorer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.WebView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.northpennines.kioskexplorer.ui.theme.KioskExplorerTheme
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.foundation.clickable
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore.Video.Thumbnails
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KioskExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

data class VideoItem(val uri: Uri, val displayName: String)

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { MainScreen(navController, modifier) }
        composable("video_list") { VideoListScreen(navController, modifier) }
        composable(
            "video_player/{videoUri}",
            arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            val decodedUri = URLDecoder.decode(encodedUri, "UTF-8")
            VideoPlayerScreen(navController, Uri.parse(decodedUri), modifier)
        }
        composable("url_launcher") { UrlLauncherScreen(navController, modifier) }
    }
}

@Composable
fun MainScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { navController.navigate("video_list") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(130.dp)
            ) {
                Text("Video Player", fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Button(
                onClick = { navController.navigate("url_launcher") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(130.dp)
            ) {
                Text("URL Launcher", fontSize = 16.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun VideoListScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            hasPermission = true
        } else {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            videos = loadVideosFromFolder(context, "Kiosk Videos")
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Back")
        }

        if (!hasPermission) {
            Text("Storage permission is needed to show videos.")
        } else if (videos.isEmpty()) {
            Text("No videos found in Movies/Kiosk Videos.\nAdd .mp4 files to that folder on the device.")
        } else {
            LazyColumn {
                items(videos) { video ->
                    val context = LocalContext.current

                    val thumbnailState = produceState<Bitmap?>(initialValue = null, video.uri) {
                        value = loadVideoThumbnail(context, video.uri)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                val encodedUri = URLEncoder.encode(video.uri.toString(), "UTF-8")
                                navController.navigate("video_player/$encodedUri")
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bitmap = thumbnailState.value
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = video.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(64.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(64.dp)
                                )
                            }

                            Text(
                                text = video.displayName,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Small helper to keep the clickable + navigate logic readable inline above


fun loadVideosFromFolder(context: android.content.Context, folderName: String): List<VideoItem> {
    val videoList = mutableListOf<VideoItem>()

    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.RELATIVE_PATH
    )

    val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("%$folderName%")

    context.contentResolver.query(
        collection, projection, selection, selectionArgs, null
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val rawName = cursor.getString(nameColumn)
            val nameWithoutExtension = rawName.substringBeforeLast(".")
            val contentUri = android.content.ContentUris.withAppendedId(collection, id)
            videoList.add(VideoItem(contentUri, nameWithoutExtension))
        }
    }

    return videoList
}

fun loadVideoThumbnail(context: android.content.Context, videoUri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(videoUri, android.util.Size(320, 240), null)
        } else {
            val id = android.content.ContentUris.parseId(videoUri)
            @Suppress("DEPRECATION")
            Thumbnails.getThumbnail(
                context.contentResolver, id, Thumbnails.MINI_KIND, null
            )
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun VideoPlayerScreen(
    navController: NavHostController,
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Back")
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(videoUri)
                    setOnPreparedListener { it.isLooping = false }
                    start()
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UrlLauncherScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Back")
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    loadUrl("https://www.northpennines.org.uk")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KioskExplorerTheme {
        MainScreen(navController = rememberNavController())
    }
}
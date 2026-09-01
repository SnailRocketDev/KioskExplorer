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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                android.graphics.Color.rgb(28, 66, 32)
            )
        )

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        hideSystemBars()

        setContent {
            KioskExplorerTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1C4220)),
                    containerColor = Color(0xFF1C4220),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    AppNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

data class VideoItem(val uri: Uri, val displayName: String)

data class UrlItem(val title: String, val url: String)

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    // Survives across screen transitions - this is the key to the fix.
    var lastBackClickTime by remember { mutableStateOf(0L) }

    val onBack: () -> Unit = {
        val now = System.currentTimeMillis()
        val currentRoute = navController.currentBackStackEntry?.destination?.route

        // Debounce rapid double-taps AND never pop away from the start destination.
        if (now - lastBackClickTime > 400L && currentRoute != "home") {
            lastBackClickTime = now
            navController.popBackStack()
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            MainScreen(navController, modifier)
        }
        composable("video_list") {
            VideoListScreen(navController = navController, onBack = onBack, modifier = modifier)
        }
        composable(
            "video_player/{videoUri}",
            arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            val decodedUri = URLDecoder.decode(encodedUri, "UTF-8")
            VideoPlayerScreen(onBack = onBack, videoUri = Uri.parse(decodedUri), modifier = modifier)
        }
        composable("url_list") {
            UrlListScreen(navController = navController, onBack = onBack, modifier = modifier)
        }
        composable(
            "url_launcher/{targetUrl}",
            arguments = listOf(navArgument("targetUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("targetUrl") ?: ""
            val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
            UrlLauncherScreen(onBack = onBack, targetUrl = decodedUrl, modifier = modifier)
        }
    }
}

@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onBack,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = modifier
    ) {
        Text("Back")
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
                modifier = Modifier.size(200.dp)
            ) {
                Text("Watch our films", fontSize = 24.sp, textAlign = TextAlign.Center)
            }
            Button(
                onClick = { navController.navigate("url_list") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(200.dp)
            ) {
                Text("Explore our sites", fontSize = 24.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ----------------------------
// VIDEO LIST SCREEN
// ----------------------------

@Composable
fun VideoListScreen(
    navController: NavHostController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        val alreadyGranted =
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            hasPermission = true
        } else {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            // The MediaStore query is I/O work - keep it off the main thread
            // so the screen doesn't hang while the list loads.
            videos = withContext(Dispatchers.IO) {
                loadVideosFromFolder(context, "Kiosk Videos")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Storage permission is needed to show videos.")
            }
        } else if (videos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "No videos found in Movies/Kiosk Videos.\n" +
                            "Add .mp4 files to that folder on the device.",
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(videos, key = { it.uri.toString() }) { video ->
                    val thumbnailState =
                        produceState<Bitmap?>(initialValue = ThumbnailCache.get(video.uri), video.uri) {
                            // Skip work entirely if it's already cached.
                            if (value == null) {
                                // Decoding is CPU/I/O heavy - do it off the main thread
                                // so scrolling stays smooth.
                                value = withContext(Dispatchers.IO) {
                                    loadVideoThumbnail(context, video.uri)
                                }
                            }
                        }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 96.dp, vertical = 8.dp)
                            .clickable {
                                val encodedUri =
                                    URLEncoder.encode(video.uri.toString(), "UTF-8")
                                navController.navigate("video_player/$encodedUri")
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFB7DB57)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bitmap = thumbnailState.value

                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = video.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(100.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(100.dp)
                                )
                            }

                            Text(
                                text = video.displayName,
                                fontSize = 26.sp,
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }
                    }
                }
            }
        }

        BackButton(
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

fun loadVideosFromFolder(
    context: android.content.Context,
    folderName: String
): List<VideoItem> {
    val videoList = mutableListOf<VideoItem>()

    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.RELATIVE_PATH
    )

    // Only allow:
    // Movies/Kiosk Videos/
    val selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf("Movies/$folderName/")

    context.contentResolver.query(
        collection,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->

        val idColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)

        val nameColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val rawName = cursor.getString(nameColumn)

            val nameWithoutExtension =
                rawName.substringBeforeLast(".")

            val contentUri =
                android.content.ContentUris.withAppendedId(
                    collection,
                    id
                )

            videoList.add(
                VideoItem(
                    contentUri,
                    nameWithoutExtension
                )
            )
        }
    }

    return videoList
}

// Thumbnails are decoded once and kept here so scrolling back to an
// item that's already been decoded is instant instead of re-decoding
// the video frame from scratch every time.
object ThumbnailCache {
    // Small LRU so we don't hold onto bitmaps for a huge library forever,
    // but recently-viewed thumbnails stay cheap to redisplay.
    private val cache = LruCache<Uri, Bitmap>(60)

    fun get(uri: Uri): Bitmap? = cache.get(uri)

    fun put(uri: Uri, bitmap: Bitmap) {
        cache.put(uri, bitmap)
    }
}

fun loadVideoThumbnail(
    context: android.content.Context,
    videoUri: Uri
): Bitmap? {
    ThumbnailCache.get(videoUri)?.let { return it }

    val bitmap = try {
        val retriever = android.media.MediaMetadataRetriever()

        retriever.setDataSource(context, videoUri)

        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                1_000_000L,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST,
                320,
                200
            )
        } else {
            retriever.getFrameAtTime(
                1_000_000L,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST
            )
        }

        retriever.release()

        frame?.copy(Bitmap.Config.ARGB_8888, false)

    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    bitmap?.let {
        ThumbnailCache.put(videoUri, it)
    }

    return bitmap
}

private fun loadVideoThumbnailLegacy(context: android.content.Context, videoUri: Uri): Bitmap? {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        // Request an already-downscaled frame instead of decoding at full
        // resolution and shrinking afterwards - far less work per item.
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                1_000_000L,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                320,
                200
            )
        } else {
            retriever.getFrameAtTime(
                1_000_000L,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
        }

        retriever.release()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        val controller = WindowCompat.getInsetsController(window, view)

        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1C4220))
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .align(Alignment.Center),
            factory = { context ->
                VideoView(context).apply {
                    val mediaController = android.widget.MediaController(context)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)

                    setVideoURI(videoUri)
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = false
                    }
                    start()
                }
            }
        )

        BackButton(
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

// ----------------------------
// URL LIST SCREEN
// ----------------------------

@Composable
fun UrlListScreen(
    navController: NavHostController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    var urls by remember { mutableStateOf<List<UrlItem>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            permissionLauncher.launch(intent)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            urls = loadUrlsFromFile()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 4.dp
            ),
        horizontalAlignment = Alignment.Start
    ) {
        if (!hasPermission) {

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("File access permission is needed to load URLs.")
            }

        } else if (urls.isEmpty()) {

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "No URLs found.\nAdd entries to Documents/Kiosk Urls/urls.txt",
                    textAlign = TextAlign.Center
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(urls) { urlItem ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(horizontal = 64.dp, vertical = 8.dp)
                            .clickable {
                                val encodedUrl =
                                    URLEncoder.encode(urlItem.url, "UTF-8")

                                navController.navigate(
                                    "url_launcher/$encodedUrl"
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFB7DB57)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = urlItem.title,
                                fontSize = 26.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        BackButton(
            onBack = onBack,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

fun loadUrlsFromFile(): List<UrlItem> {
    val file = java.io.File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
        "Kiosk Urls/urls.txt"
    )

    if (!file.exists()) return emptyList()

    val urlList = mutableListOf<UrlItem>()

    file.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && trimmed.contains("|")) {
            val parts = trimmed.split("|", limit = 2)
            val title = parts[0].trim()
            val url = parts[1].trim()
            if (title.isNotEmpty() && url.isNotEmpty()) {
                urlList.add(UrlItem(title, url))
            }
        }
    }

    return urlList
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UrlLauncherScreen(
    onBack: () -> Unit,
    targetUrl: String,
    modifier: Modifier = Modifier
) {
    val allowedHost = Uri.parse(targetUrl).host ?: ""
    // Get the base domain (last two parts) so subdomains of the same site are allowed
    val allowedBaseDomain = allowedHost.split(".").takeLast(2).joinToString(".")

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    setOnLongClickListener { true }
                    isLongClickable = false

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val requestedHost = request?.url?.host ?: return true
                            val isSameSite = requestedHost.endsWith(allowedBaseDomain)
                            return !isSameSite
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            android.util.Log.e(
                                "KioskWebView",
                                "Load error: ${error?.description} for ${request?.url}"
                            )
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: android.webkit.SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            android.util.Log.e("KioskWebView", "SSL error: $error")
                            handler?.cancel()
                        }
                    }

                    loadUrl(targetUrl)
                }
            }
        )

        BackButton(
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
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
package com.ultron.chat.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The real 3D Ultron head. The avatar is a self-contained WebGL scene
 * (Three.js, procedural model, no network) living in a hardware-accelerated
 * WebView — the approach the model's own integration guide recommends. Only
 * this one view is a WebView; the rest of the app stays native Kotlin/Compose.
 *
 * [state] is one of idle / listening / thinking / speaking and is pushed into
 * the scene via `window.ultronFace.setState(...)`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AvatarWebView(state: String, modifier: Modifier = Modifier) {
    var ready by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    allowContentAccess = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(INJECT_CSS, null)
                        view.evaluateJavascript(setStateJs(state), null)
                        ready = true
                    }
                }
                loadUrl("file:///android_asset/avatar/ultron.html")
            }
        },
        update = { view ->
            if (ready) view.evaluateJavascript(setStateJs(state), null)
        },
        onRelease = { it.destroy() },
    )
}

/** Guarded so a not-yet-ready scene (hot state change mid-load) never throws. */
private fun setStateJs(state: String): String =
    "try{window.ultronFace&&window.ultronFace.setState('$state')}catch(e){}"

/**
 * Strips the preview's demo chrome (buttons, sliders, captions) and lets the
 * head fill the view, retheming the stage glow to the app's red-on-black.
 */
private const val INJECT_CSS = """
(function(){
  var css = document.getElementById('ultron-embed-style');
  if(!css){ css = document.createElement('style'); css.id='ultron-embed-style'; document.head.appendChild(css); }
  css.textContent =
    'html,body{margin:0!important;padding:0!important;background:transparent!important;overflow:hidden!important;height:100%!important}'+
    '.viz-controls,.ultron-caption,.text-small,.text-muted,.form-label,.form-control,.form-range,.form-select{display:none!important}'+
    '#ultron-prime-preview{width:100vw!important;height:100vh!important;margin:0!important;padding:0!important;background:transparent!important;max-width:none!important}'+
    '.ultron-stage{width:100vw!important;height:100vh!important;margin:0!important;border:0!important;border-radius:0!important;'+
      'background:radial-gradient(ellipse at 50% 42%, #2a0a12 0%, #120408 46%, #0b0b0d 100%)!important}'+
    'canvas{background:transparent!important}';
})();
"""

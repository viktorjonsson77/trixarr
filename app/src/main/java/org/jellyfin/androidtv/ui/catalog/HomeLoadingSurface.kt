package org.jellyfin.androidtv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Same hero/row geometry as Home, with no fabricated titles, images or focus targets. */
@Composable
internal fun HomeLoadingSurface() {
    @Composable fun Placeholder(modifier: Modifier) {
        Box(modifier.background(Color(0xFF192131), RoundedCornerShape(5.dp)))
    }
    Column(Modifier.fillMaxSize().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Spacer(Modifier.height(240.dp))
            Column(Modifier.height(126.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.height(57.dp)) {
                    Placeholder(Modifier.padding(top = 28.dp).width(280.dp).height(24.dp))
                }
                Placeholder(Modifier.width(160.dp).height(12.dp))
                Placeholder(Modifier.width(400.dp).height(28.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Placeholder(Modifier.width(96.dp).height(42.dp))
                Placeholder(Modifier.width(156.dp).height(42.dp))
            }
        }
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Placeholder(Modifier.width(132.dp).height(21.dp))
                Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(5) { Placeholder(Modifier.width(168.dp).height(96.dp)) }
                }
            }
        }
    }
}

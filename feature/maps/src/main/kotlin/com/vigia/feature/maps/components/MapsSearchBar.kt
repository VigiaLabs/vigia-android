package com.vigia.feature.maps.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vigia.core.model.SearchPlace

private val BarBackground = Color(0xCC0A0A0F)
private val BarBorder     = Color(0x1AFFFFFF)
private val TextColor     = Color.White
private val HintColor     = Color.White.copy(alpha = 0.35f)

@Composable
fun MapsSearchBar(
    query: String,
    results: List<SearchPlace>,
    isActive: Boolean,
    onQueryChange: (String) -> Unit,
    onResultSelected: (SearchPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BarBackground)
                .border(0.5.dp, BarBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.Search,
                contentDescription = null,
                tint               = HintColor,
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value          = query,
                onValueChange  = onQueryChange,
                singleLine     = true,
                textStyle      = MaterialTheme.typography.bodyMedium.copy(color = TextColor),
                cursorBrush    = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* handled by results */ }),
                modifier       = Modifier.weight(1f),
                decorationBox  = { inner ->
                    if (query.isEmpty()) {
                        Text("Search hazards, routes, zones…", style = MaterialTheme.typography.bodyMedium, color = HintColor)
                    }
                    inner()
                },
            )
            if (isActive) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, null, tint = HintColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Results dropdown
        AnimatedVisibility(
            visible = isActive && results.isNotEmpty(),
            enter   = fadeIn() + slideInVertically(),
            exit    = fadeOut() + slideOutVertically(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(BarBackground)
                    .border(0.5.dp, BarBorder, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
            ) {
                items(results, key = { it.id }) { place ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultSelected(place) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(place.name, style = MaterialTheme.typography.bodyMedium, color = TextColor)
                        Text(place.address, style = MaterialTheme.typography.bodySmall, color = HintColor)
                    }
                    HorizontalDivider(color = BarBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

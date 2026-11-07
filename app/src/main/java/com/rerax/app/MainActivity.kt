package com.rerax.app

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Item(val id: String, val title: String, val time: String, val done: Boolean)

private fun load(ctx: Context): List<Item> {
    val raw = ctx.getSharedPreferences("rerax", Context.MODE_PRIVATE)
        .getString("items", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Item(o.getString("id"), o.getString("title"), o.getString("time"), o.getBoolean("done"))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun save(ctx: Context, items: List<Item>) {
    val arr = JSONArray()
    items.forEach {
        arr.put(
            JSONObject().put("id", it.id).put("title", it.title)
                .put("time", it.time).put("done", it.done)
        )
    }
    ctx.getSharedPreferences("rerax", Context.MODE_PRIVATE)
        .edit().putString("items", arr.toString()).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReraxApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReraxApp() {
    val ctx = LocalContext.current
    val list = remember { mutableStateListOf<Item>().apply { addAll(load(ctx)) } }
    var showAdd by remember { mutableStateOf(false) }
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF4FC3AB))
    } else {
        lightColorScheme(primary = Color(0xFF1F7A6A))
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Rerax", fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(
                            onClick = {
                                for (i in list.indices) list[i] = list[i].copy(done = false)
                                save(ctx, list.toList())
                            },
                            enabled = list.isNotEmpty()
                        ) { Text("Zurücksetzen") }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Text("+", fontSize = 28.sp)
                }
            }
        ) { pad ->
            if (list.isEmpty()) {
                Column(
                    Modifier.padding(pad).fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Noch keine Routine", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tippe auf +, um deinen ersten Punkt hinzuzufügen.",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    Modifier.padding(pad).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "${list.count { it.done }} von ${list.size} erledigt",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(list, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.done,
                                    onCheckedChange = { c ->
                                        val i = list.indexOfFirst { it.id == item.id }
                                        if (i >= 0) {
                                            list[i] = list[i].copy(done = c)
                                            save(ctx, list.toList())
                                        }
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                        color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        item.time + " Uhr",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    list.removeAll { it.id == item.id }
                                    save(ctx, list.toList())
                                }) { Text("×", fontSize = 24.sp) }
                            }
                        }
                    }
                }
            }
        }

        if (showAdd) {
            AddDialog(
                onDismiss = { showAdd = false },
                onAdd = { title, time ->
                    list.add(Item(UUID.randomUUID().toString(), title, time, false))
                    list.sortBy { it.time }
                    save(ctx, list.toList())
                    showAdd = false
                }
            )
        }
    }
}

@Composable
fun AddDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("07:00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer Punkt") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Was machst du?") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    val parts = time.split(":")
                    TimePickerDialog(
                        ctx,
                        { _, hh, mm -> time = "%02d:%02d".format(hh, mm) },
                        parts[0].toInt(), parts[1].toInt(), true
                    ).show()
                }) { Text("Uhrzeit: $time") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title.trim(), time) },
                enabled = title.isNotBlank()
            ) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

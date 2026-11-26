package com.ryda.app.ui.screens

import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.ui.theme.AsphaltBlack
import com.ryda.app.ui.theme.BorderSubtle
import com.ryda.app.ui.theme.CarbonDark
import com.ryda.app.ui.theme.CrimsonRed
import com.ryda.app.ui.theme.ForestGreen
import com.ryda.app.ui.theme.HighVisLime
import com.ryda.app.ui.theme.SafetyAmber
import com.ryda.app.ui.theme.SurfaceCard
import com.ryda.app.ui.theme.TextPrimary
import com.ryda.app.ui.theme.TextSecondary

@Composable
fun GarageScreen(
    motorcycles: List<Motorcycle>,
    onAddMotorcycle: (Motorcycle) -> Unit,
    onEditMotorcycle: (Motorcycle) -> Unit = {},
    onSetPrimary: (String) -> Unit,
    onDeleteMotorcycle: (String) -> Unit,
    onViewStats: (String) -> Unit = {},
    onBackClick: (() -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var bikeToEdit by remember { mutableStateOf<Motorcycle?>(null) }

    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var yearStr by remember { mutableStateOf("2024") }
    var ccStr by remember { mutableStateOf("450") }
    var nickname by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AsphaltBlack)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Column {
                    Text(
                        text = "DIGITAL GARAGE",
                        color = SafetyAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "My Motorcycles",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Bike", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(motorcycles) { bike ->
                MotorcycleCard(
                    bike = bike,
                    onSetPrimary = { onSetPrimary(bike.id) },
                    onViewStats = { onViewStats(bike.id) },
                    onEdit = { bikeToEdit = bike },
                    onDelete = { onDeleteMotorcycle(bike.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Add Motorcycle Dialog
    if (showAddDialog) {
        var bikeImageUri by remember { mutableStateOf<String?>(null) }
        val addPhotoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let { bikeImageUri = it.toString() }
        }
        val addLegacyPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { bikeImageUri = it.toString() }
        }

        fun pickBikePhoto() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                addLegacyPicker.launch("image/*")
            }
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Motorcycle", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { pickBikePhoto() }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (bikeImageUri != null) "Photo attached ✓" else "Attach Bike Photo (Optional)", color = if (bikeImageUri != null) SafetyAmber else TextPrimary, fontSize = 12.sp)
                        }
                        Text(text = if (bikeImageUri != null) "Change" else "Choose", color = TextSecondary, fontSize = 11.sp)
                    }

                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Make / Brand (e.g. Royal Enfield)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model (e.g. Himalayan 450)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = variant,
                        onValueChange = { variant = it },
                        label = { Text("Variant / Color (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = yearStr,
                            onValueChange = { yearStr = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ccStr,
                            onValueChange = { ccStr = it },
                            label = { Text("Engine (CC)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Nickname (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (brand.isNotBlank() && model.isNotBlank()) {
                            onAddMotorcycle(
                                Motorcycle(
                                    brand = brand.trim(),
                                    model = model.trim(),
                                    variant = variant.takeIf { it.isNotBlank() },
                                    year = yearStr.toIntOrNull() ?: 2024,
                                    engineCc = ccStr.toIntOrNull() ?: 450,
                                    nickname = nickname.takeIf { it.isNotBlank() },
                                    imageUri = bikeImageUri
                                )
                            )
                            brand = ""
                            model = ""
                            variant = ""
                            nickname = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                ) {
                    Text("Add to Garage")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }

    // Edit Motorcycle Dialog
    bikeToEdit?.let { currentBike ->
        var editBrand by remember(currentBike) { mutableStateOf(currentBike.brand) }
        var editModel by remember(currentBike) { mutableStateOf(currentBike.model) }
        var editVariant by remember(currentBike) { mutableStateOf(currentBike.variant ?: "") }
        var editYearStr by remember(currentBike) { mutableStateOf(currentBike.year.toString()) }
        var editCcStr by remember(currentBike) { mutableStateOf(currentBike.engineCc.toString()) }
        var editNickname by remember(currentBike) { mutableStateOf(currentBike.nickname ?: "") }
        var editImageUri by remember(currentBike) { mutableStateOf(currentBike.imageUri) }

        val editPhotoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let { editImageUri = it.toString() }
        }
        val editLegacyPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { editImageUri = it.toString() }
        }

        fun pickEditBikePhoto() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                editPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                editLegacyPicker.launch("image/*")
            }
        }

        AlertDialog(
            onDismissRequest = { bikeToEdit = null },
            title = { Text("Edit Motorcycle", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { pickEditBikePhoto() }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (editImageUri != null) "Photo attached ✓" else "Attach Bike Photo (Optional)", color = if (editImageUri != null) SafetyAmber else TextPrimary, fontSize = 12.sp)
                        }
                        Text(text = if (editImageUri != null) "Change" else "Choose", color = TextSecondary, fontSize = 11.sp)
                    }

                    OutlinedTextField(
                        value = editBrand,
                        onValueChange = { editBrand = it },
                        label = { Text("Make / Brand") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editModel,
                        onValueChange = { editModel = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editVariant,
                        onValueChange = { editVariant = it },
                        label = { Text("Variant / Color") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editYearStr,
                            onValueChange = { editYearStr = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editCcStr,
                            onValueChange = { editCcStr = it },
                            label = { Text("Engine (CC)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editBrand.isNotBlank() && editModel.isNotBlank()) {
                            onEditMotorcycle(
                                currentBike.copy(
                                    brand = editBrand.trim(),
                                    model = editModel.trim(),
                                    variant = editVariant.takeIf { it.isNotBlank() },
                                    year = editYearStr.toIntOrNull() ?: currentBike.year,
                                    engineCc = editCcStr.toIntOrNull() ?: currentBike.engineCc,
                                    nickname = editNickname.takeIf { it.isNotBlank() },
                                    imageUri = editImageUri
                                )
                            )
                            bikeToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber, contentColor = AsphaltBlack)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { bikeToEdit = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CarbonDark
        )
    }
}

@Composable
fun MotorcycleCard(
    bike: Motorcycle,
    onSetPrimary: () -> Unit,
    onViewStats: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewStats() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CarbonDark),
        border = BorderStroke(
            1.dp,
            if (bike.isPrimary) SafetyAmber else BorderSubtle
        )
    ) {
        Column {
            if (!bike.imageUri.isNullOrBlank()) {
                RideImageThumbnail(
                    photoUri = bike.imageUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = null,
                            tint = if (bike.isPrimary) SafetyAmber else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${bike.brand} ${bike.model}",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!bike.nickname.isNullOrBlank()) {
                                Text(
                                    text = "\"${bike.nickname}\"",
                                    color = SafetyAmber,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (bike.isPrimary) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "DEFAULT", color = ForestGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${bike.year} • ${bike.engineCc} cc ${if (bike.variant != null) "• ${bike.variant}" else ""}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onViewStats,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.BarChart, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stats & History", fontSize = 12.sp, color = TextPrimary)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!bike.isPrimary) {
                            OutlinedButton(
                                onClick = onSetPrimary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Default", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = SafetyAmber)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRed)
                        }
                    }
                }
            }
        }
    }
}

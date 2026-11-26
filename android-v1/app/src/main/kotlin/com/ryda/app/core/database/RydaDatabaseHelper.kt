package com.ryda.app.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ryda.app.core.models.Club
import com.ryda.app.core.models.CommunityPost
import com.ryda.app.core.models.GpsPoint
import com.ryda.app.core.models.MediaSyncStatus
import com.ryda.app.core.models.Motorcycle
import com.ryda.app.core.models.OutboxOperation
import com.ryda.app.core.models.OutboxOperationType
import com.ryda.app.core.models.OutboxStatus
import com.ryda.app.core.models.Ride
import com.ryda.app.core.models.RideStatus
import com.ryda.app.core.models.RideTelemetrySnippet
import com.ryda.app.core.models.RiderProfile
import com.ryda.app.core.models.RidingEvent
import com.ryda.app.core.models.SyncStatus
import java.util.UUID

class RydaDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ryda_offline.db"
        private const val DATABASE_VERSION = 8

        @Volatile
        private var INSTANCE: RydaDatabaseHelper? = null

        fun getInstance(context: Context): RydaDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RydaDatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTablesAndSeedData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE rider_profile ADD COLUMN avatar_url TEXT DEFAULT '';")
            } catch (_: Exception) {}
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE rides ADD COLUMN photo_uri TEXT;")
            } catch (_: Exception) {}
        }
        if (oldVersion < 4) {
            createTablesAndSeedData(db)
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE motorcycles ADD COLUMN image_uri TEXT;")
            } catch (_: Exception) {}
        }
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE rider_profile ADD COLUMN active_tag_id TEXT;")
                db.execSQL("ALTER TABLE rider_profile ADD COLUMN active_tag_name TEXT;")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_achievements (
                        user_id TEXT NOT NULL,
                        achievement_id TEXT NOT NULL,
                        progress_value REAL DEFAULT 0,
                        is_unlocked INTEGER DEFAULT 0,
                        unlocked_at TEXT,
                        PRIMARY KEY (user_id, achievement_id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rider_tags (
                        user_id TEXT NOT NULL,
                        tag_id TEXT NOT NULL,
                        unlocked_at TEXT,
                        PRIMARY KEY (user_id, tag_id)
                    )
                    """.trimIndent()
                )
            } catch (_: Exception) {}
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE rides ADD COLUMN media_sync_status TEXT DEFAULT 'PENDING';")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        id TEXT PRIMARY KEY,
                        entity_type TEXT NOT NULL,
                        entity_id TEXT NOT NULL,
                        server_id TEXT,
                        operation_type TEXT NOT NULL,
                        payload_json TEXT DEFAULT '{}',
                        timestamp INTEGER NOT NULL,
                        attempt_count INTEGER DEFAULT 0,
                        last_error TEXT,
                        status TEXT DEFAULT 'PENDING'
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_outbox_status ON sync_outbox(status);")
            } catch (_: Exception) {}
        }
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE clubs ADD COLUMN my_role TEXT;")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE rides ADD COLUMN club_ride_session_id TEXT;")
            } catch (_: Exception) {}
        }
    }

    private fun createTablesAndSeedData(db: SQLiteDatabase) {
        try {
            // Table: rides
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS rides (
                    id TEXT PRIMARY KEY,
                    motorcycle_id TEXT,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER,
                    total_distance_meters REAL DEFAULT 0,
                    moving_time_seconds INTEGER DEFAULT 0,
                    elapsed_time_seconds INTEGER DEFAULT 0,
                    max_speed_kmh REAL DEFAULT 0,
                    avg_speed_kmh REAL DEFAULT 0,
                    elevation_gain_meters REAL DEFAULT 0,
                    sync_status TEXT NOT NULL,
                    media_sync_status TEXT DEFAULT 'PENDING',
                    is_private INTEGER DEFAULT 1,
                    photo_uri TEXT,
                    club_ride_session_id TEXT
                )
                """.trimIndent()
            )

            // Table: gps_points
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS gps_points (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ride_id TEXT NOT NULL,
                    sequence_number INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    altitude REAL DEFAULT 0,
                    accuracy REAL DEFAULT 0,
                    speed_kmh REAL DEFAULT 0,
                    FOREIGN KEY (ride_id) REFERENCES rides(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_gps_points_ride_id ON gps_points(ride_id);")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_gps_points_seq ON gps_points(ride_id, sequence_number);")

            // Table: motorcycles
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS motorcycles (
                    id TEXT PRIMARY KEY,
                    brand TEXT NOT NULL,
                    model TEXT NOT NULL,
                    variant TEXT,
                    year INTEGER DEFAULT 2024,
                    engine_cc INTEGER DEFAULT 450,
                    nickname TEXT,
                    is_primary INTEGER DEFAULT 0,
                    image_uri TEXT
                )
                """.trimIndent()
            )

            // Table: rider_profile
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS rider_profile (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    bio TEXT,
                    city TEXT,
                    avatar_url TEXT DEFAULT '',
                    is_profile_public INTEGER DEFAULT 0,
                    total_rides INTEGER DEFAULT 0,
                    total_distance_km REAL DEFAULT 0,
                    total_time_hours REAL DEFAULT 0,
                    active_tag_id TEXT,
                    active_tag_name TEXT
                )
                """.trimIndent()
            )

            // Table: user_achievements
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_achievements (
                    user_id TEXT NOT NULL,
                    achievement_id TEXT NOT NULL,
                    progress_value REAL DEFAULT 0,
                    is_unlocked INTEGER DEFAULT 0,
                    unlocked_at TEXT,
                    PRIMARY KEY (user_id, achievement_id)
                )
                """.trimIndent()
            )

            // Table: rider_tags
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS rider_tags (
                    user_id TEXT NOT NULL,
                    tag_id TEXT NOT NULL,
                    unlocked_at TEXT,
                    PRIMARY KEY (user_id, tag_id)
                )
                """.trimIndent()
            )

            // Table: sync_outbox
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_outbox (
                    id TEXT PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    server_id TEXT,
                    operation_type TEXT NOT NULL,
                    payload_json TEXT DEFAULT '{}',
                    timestamp INTEGER NOT NULL,
                    attempt_count INTEGER DEFAULT 0,
                    last_error TEXT,
                    status TEXT DEFAULT 'PENDING'
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_outbox_status ON sync_outbox(status);")

            // Table: community_posts
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS community_posts (
                    id TEXT PRIMARY KEY,
                    author_id INTEGER NOT NULL DEFAULT 1,
                    author_username TEXT NOT NULL,
                    author_display_name TEXT DEFAULT '',
                    author_avatar_url TEXT DEFAULT '',
                    author_city TEXT DEFAULT '',
                    post_type TEXT DEFAULT 'RIDE',
                    caption TEXT DEFAULT '',
                    image_url TEXT DEFAULT '',
                    created_at TEXT DEFAULT '',
                    ride_id TEXT DEFAULT '',
                    ride_title TEXT DEFAULT '',
                    total_distance_km REAL DEFAULT 0,
                    moving_time_seconds INTEGER DEFAULT 0,
                    avg_speed_kmh REAL DEFAULT 0,
                    motorcycle_name TEXT DEFAULT 'Motorcycle',
                    likes_count INTEGER DEFAULT 0,
                    comments_count INTEGER DEFAULT 0,
                    is_liked INTEGER DEFAULT 0,
                    is_following_author INTEGER DEFAULT 0
                )
                """.trimIndent()
            )

            // Table: clubs
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS clubs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    slug TEXT DEFAULT '',
                    description TEXT DEFAULT '',
                    city TEXT DEFAULT 'Mumbai',
                    banner_url TEXT DEFAULT '',
                    logo_url TEXT DEFAULT '',
                    is_verified INTEGER DEFAULT 1,
                    policy TEXT DEFAULT 'OPEN',
                    total_mileage_km REAL DEFAULT 0,
                    members_count INTEGER DEFAULT 1,
                    is_member INTEGER DEFAULT 0,
                    my_role TEXT
                )
                """.trimIndent()
            )

            // Table: rally_events
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS rally_events (
                    id TEXT PRIMARY KEY,
                    club_id TEXT DEFAULT '',
                    club_name TEXT DEFAULT '',
                    title TEXT NOT NULL,
                    description TEXT DEFAULT '',
                    meeting_point TEXT DEFAULT '',
                    destination TEXT DEFAULT '',
                    start_time TEXT DEFAULT '',
                    rider_limit INTEGER DEFAULT 35,
                    pace TEXT DEFAULT 'Intermediate',
                    distance_km REAL DEFAULT 100,
                    is_official INTEGER DEFAULT 1,
                    confirmed_count INTEGER DEFAULT 1,
                    interested_count INTEGER DEFAULT 0,
                    my_rsvp TEXT DEFAULT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) {}
    }

    // --- RIDE OPERATIONS ---

    fun insertRide(ride: Ride) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("id", ride.id)
                put("motorcycle_id", ride.motorcycleId)
                put("title", ride.title)
                put("status", ride.status.name)
                put("start_time", ride.startTime)
                put("end_time", ride.endTime)
                put("total_distance_meters", ride.totalDistanceMeters)
                put("moving_time_seconds", ride.movingTimeSeconds)
                put("elapsed_time_seconds", ride.elapsedTimeSeconds)
                put("max_speed_kmh", ride.maxSpeedKmh)
                put("avg_speed_kmh", ride.avgSpeedKmh)
                put("elevation_gain_meters", ride.elevationGainMeters)
                put("sync_status", ride.syncStatus.name)
                put("media_sync_status", ride.mediaSyncStatus.name)
                put("is_private", if (ride.isPrivate) 1 else 0)
                put("photo_uri", ride.photoUri)
                put("club_ride_session_id", ride.clubRideSessionId)
            }
            db.insertWithOnConflict(
                "rides",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun updateRide(ride: Ride) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("motorcycle_id", ride.motorcycleId)
                put("title", ride.title)
                put("status", ride.status.name)
                put("end_time", ride.endTime)
                put("total_distance_meters", ride.totalDistanceMeters)
                put("moving_time_seconds", ride.movingTimeSeconds)
                put("elapsed_time_seconds", ride.elapsedTimeSeconds)
                put("max_speed_kmh", ride.maxSpeedKmh)
                put("avg_speed_kmh", ride.avgSpeedKmh)
                put("elevation_gain_meters", ride.elevationGainMeters)
                put("sync_status", ride.syncStatus.name)
                put("media_sync_status", ride.mediaSyncStatus.name)
                put("is_private", if (ride.isPrivate) 1 else 0)
                put("photo_uri", ride.photoUri)
                put("club_ride_session_id", ride.clubRideSessionId)
            }
            db.update("rides", values, "id = ?", arrayOf(ride.id))
        }
    }

    private fun cursorToRide(cursor: android.database.Cursor): Ride {
        val mediaIdx = cursor.getColumnIndex("media_sync_status")
        val mediaStatusStr = if (mediaIdx != -1) cursor.getString(mediaIdx) else null
        val mediaStatus = if (!mediaStatusStr.isNullOrBlank()) try { MediaSyncStatus.valueOf(mediaStatusStr) } catch (_: Exception) { MediaSyncStatus.PENDING } else MediaSyncStatus.PENDING
        val sessionIdx = cursor.getColumnIndex("club_ride_session_id")
        val sessionId = if (sessionIdx != -1 && !cursor.isNull(sessionIdx)) cursor.getString(sessionIdx) else null

        return Ride(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            motorcycleId = cursor.getString(cursor.getColumnIndexOrThrow("motorcycle_id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            status = RideStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
            startTime = cursor.getLong(cursor.getColumnIndexOrThrow("start_time")),
            endTime = if (cursor.isNull(cursor.getColumnIndexOrThrow("end_time"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("end_time")),
            totalDistanceMeters = cursor.getDouble(cursor.getColumnIndexOrThrow("total_distance_meters")),
            movingTimeSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("moving_time_seconds")),
            elapsedTimeSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("elapsed_time_seconds")),
            maxSpeedKmh = cursor.getDouble(cursor.getColumnIndexOrThrow("max_speed_kmh")),
            avgSpeedKmh = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_speed_kmh")),
            elevationGainMeters = cursor.getDouble(cursor.getColumnIndexOrThrow("elevation_gain_meters")),
            syncStatus = SyncStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("sync_status"))),
            mediaSyncStatus = mediaStatus,
            isPrivate = cursor.getInt(cursor.getColumnIndexOrThrow("is_private")) == 1,
            photoUri = if (cursor.isNull(cursor.getColumnIndexOrThrow("photo_uri"))) null else cursor.getString(cursor.getColumnIndexOrThrow("photo_uri")),
            clubRideSessionId = sessionId
        )
    }

    fun getAllCompletedRides(): List<Ride> {
        val list = mutableListOf<Ride>()
        readableDatabase.rawQuery(
            "SELECT * FROM rides WHERE status = 'COMPLETED' ORDER BY start_time DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToRide(cursor))
            }
        }
        return list
    }

    fun getRideById(rideId: String): Ride? {
        readableDatabase.rawQuery("SELECT * FROM rides WHERE id = ?", arrayOf(rideId)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToRide(cursor)
            }
        }
        return null
    }

    fun getRideByClubSessionId(sessionId: String): Ride? {
        readableDatabase.rawQuery(
            "SELECT * FROM rides WHERE club_ride_session_id = ? LIMIT 1",
            arrayOf(sessionId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToRide(cursor)
            }
        }
        return null
    }

    fun deleteRide(rideId: String) {
        writableDatabase.use { db ->
            db.delete("gps_points", "ride_id = ?", arrayOf(rideId))
            db.delete("rides", "id = ?", arrayOf(rideId))
        }
    }

    fun getIncompleteRide(): Ride? {
        readableDatabase.rawQuery(
            "SELECT * FROM rides WHERE status IN ('RECORDING', 'PAUSED') ORDER BY start_time DESC LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToRide(cursor)
            }
        }
        return null
    }

    // --- OUTBOX OPERATIONS ---

    fun insertOutboxOperation(operation: OutboxOperation, database: SQLiteDatabase? = null) {
        val targetDb = database ?: writableDatabase
        val values = ContentValues().apply {
            put("id", operation.id)
            put("entity_type", operation.entityType)
            put("entity_id", operation.entityId)
            put("server_id", operation.serverId)
            put("operation_type", operation.operationType.name)
            put("payload_json", operation.payloadJson)
            put("timestamp", operation.timestamp)
            put("attempt_count", operation.attemptCount)
            put("last_error", operation.lastError)
            put("status", operation.status.name)
        }
        targetDb.insertWithOnConflict("sync_outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getPendingOutboxOperations(): List<OutboxOperation> {
        val list = mutableListOf<OutboxOperation>()
        readableDatabase.rawQuery(
            "SELECT * FROM sync_outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY timestamp ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    OutboxOperation(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        entityType = cursor.getString(cursor.getColumnIndexOrThrow("entity_type")),
                        entityId = cursor.getString(cursor.getColumnIndexOrThrow("entity_id")),
                        serverId = cursor.getString(cursor.getColumnIndexOrThrow("server_id")),
                        operationType = OutboxOperationType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("operation_type"))),
                        payloadJson = cursor.getString(cursor.getColumnIndexOrThrow("payload_json")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        attemptCount = cursor.getInt(cursor.getColumnIndexOrThrow("attempt_count")),
                        lastError = cursor.getString(cursor.getColumnIndexOrThrow("last_error")),
                        status = OutboxStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
                    )
                )
            }
        }
        return list
    }

    fun updateOutboxOperation(operation: OutboxOperation) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("attempt_count", operation.attemptCount)
                put("last_error", operation.lastError)
                put("status", operation.status.name)
                put("server_id", operation.serverId)
            }
            db.update("sync_outbox", values, "id = ?", arrayOf(operation.id))
        }
    }

    fun deleteOutboxOperation(id: String) {
        writableDatabase.use { db ->
            db.delete("sync_outbox", "id = ?", arrayOf(id))
        }
    }

    fun updateRideMotorcycle(rideId: String, motorcycleId: String?) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("motorcycle_id", motorcycleId)
                put("sync_status", SyncStatus.LOCAL_ONLY.name)
            }
            db.update("rides", values, "id = ?", arrayOf(rideId))
        }
    }

    fun updateRidePhoto(rideId: String, photoUri: String?) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("photo_uri", photoUri)
                put("sync_status", SyncStatus.LOCAL_ONLY.name)
            }
            db.update("rides", values, "id = ?", arrayOf(rideId))
        }
    }

    fun finishIncompleteRide(rideId: String): Ride? {
        val existing = getRideById(rideId) ?: return null
        val points = getPointsForRide(rideId)

        var totalDist = 0.0
        var maxSpeed = 0.0
        var totalSpeed = 0.0
        var movingSecs = 0L

        if (points.size >= 2) {
            for (i in 1 until points.size) {
                val p1 = points[i - 1]
                val p2 = points[i]
                val d = com.ryda.app.core.location.Haversine.distanceInMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                totalDist += d
                val dt = (p2.timestamp - p1.timestamp) / 1000L
                if (p2.speedKmh > 5.0 && dt in 1..30) {
                    movingSecs += dt
                }
                if (p2.speedKmh > maxSpeed) maxSpeed = p2.speedKmh
                totalSpeed += p2.speedKmh
            }
        }

        val elapsedSecs = if (points.isNotEmpty()) {
            maxOf(movingSecs, (points.last().timestamp - points.first().timestamp) / 1000L)
        } else {
            existing.elapsedTimeSeconds
        }
        val avgSpeed = if (points.isNotEmpty()) totalSpeed / points.size else 0.0

        val completedRide = existing.copy(
            status = RideStatus.COMPLETED,
            endTime = if (points.isNotEmpty()) points.last().timestamp else System.currentTimeMillis(),
            totalDistanceMeters = if (totalDist > 0) totalDist else existing.totalDistanceMeters,
            movingTimeSeconds = if (movingSecs > 0) movingSecs else existing.movingTimeSeconds,
            elapsedTimeSeconds = elapsedSecs,
            maxSpeedKmh = if (maxSpeed > 0) maxSpeed else existing.maxSpeedKmh,
            avgSpeedKmh = if (avgSpeed > 0) avgSpeed else existing.avgSpeedKmh,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        updateRide(completedRide)
        return completedRide
    }

    // --- GPS POINT OPERATIONS ---

    fun insertGpsPoint(point: GpsPoint) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("ride_id", point.rideId)
                put("sequence_number", point.sequenceNumber)
                put("timestamp", point.timestamp)
                put("latitude", point.latitude)
                put("longitude", point.longitude)
                put("altitude", point.altitude)
                put("accuracy", point.accuracy)
                put("speed_kmh", point.speedKmh)
            }
            db.insert("gps_points", null, values)
        }
    }

    fun insertGpsPointsBatch(points: List<GpsPoint>) {
        if (points.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (p in points) {
                val values = ContentValues().apply {
                    put("ride_id", p.rideId)
                    put("sequence_number", p.sequenceNumber)
                    put("timestamp", p.timestamp)
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                    put("altitude", p.altitude)
                    put("accuracy", p.accuracy)
                    put("speed_kmh", p.speedKmh)
                }
                db.insert("gps_points", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPointsForRide(rideId: String): List<GpsPoint> {
        val list = mutableListOf<GpsPoint>()
        readableDatabase.rawQuery(
            "SELECT * FROM gps_points WHERE ride_id = ? ORDER BY sequence_number ASC",
            arrayOf(rideId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    GpsPoint(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        rideId = cursor.getString(cursor.getColumnIndexOrThrow("ride_id")),
                        sequenceNumber = cursor.getLong(cursor.getColumnIndexOrThrow("sequence_number")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                        longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                        altitude = cursor.getDouble(cursor.getColumnIndexOrThrow("altitude")),
                        accuracy = cursor.getFloat(cursor.getColumnIndexOrThrow("accuracy")),
                        speedKmh = cursor.getDouble(cursor.getColumnIndexOrThrow("speed_kmh"))
                    )
                )
            }
        }
        return list
    }

    fun processGpsPointsInChunks(
        rideId: String,
        chunkSize: Int = 1000,
        action: (List<GpsPoint>) -> Unit
    ) {
        var lastSeq = -1L
        while (true) {
            val chunk = mutableListOf<GpsPoint>()
            readableDatabase.rawQuery(
                "SELECT * FROM gps_points WHERE ride_id = ? AND sequence_number > ? ORDER BY sequence_number ASC LIMIT ?",
                arrayOf(rideId, lastSeq.toString(), chunkSize.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val pt = GpsPoint(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        rideId = cursor.getString(cursor.getColumnIndexOrThrow("ride_id")),
                        sequenceNumber = cursor.getLong(cursor.getColumnIndexOrThrow("sequence_number")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                        longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                        altitude = cursor.getDouble(cursor.getColumnIndexOrThrow("altitude")),
                        accuracy = cursor.getFloat(cursor.getColumnIndexOrThrow("accuracy")),
                        speedKmh = cursor.getDouble(cursor.getColumnIndexOrThrow("speed_kmh"))
                    )
                    chunk.add(pt)
                    lastSeq = pt.sequenceNumber
                }
            }

            if (chunk.isEmpty()) break
            action(chunk)
            if (chunk.size < chunkSize) break
        }
    }

    fun getSimplifiedPointsForRide(rideId: String, maxPoints: Int = 1000): List<GpsPoint> {
        val totalCount = getGpsPointsCount(rideId)
        if (totalCount <= maxPoints) {
            return getPointsForRide(rideId)
        }
        val step = (totalCount.toDouble() / maxPoints).toInt().coerceAtLeast(1)
        val list = mutableListOf<GpsPoint>()
        readableDatabase.rawQuery(
            "SELECT * FROM gps_points WHERE ride_id = ? AND sequence_number % ? = 0 ORDER BY sequence_number ASC",
            arrayOf(rideId, step.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    GpsPoint(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        rideId = cursor.getString(cursor.getColumnIndexOrThrow("ride_id")),
                        sequenceNumber = cursor.getLong(cursor.getColumnIndexOrThrow("sequence_number")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                        longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                        altitude = cursor.getDouble(cursor.getColumnIndexOrThrow("altitude")),
                        accuracy = cursor.getFloat(cursor.getColumnIndexOrThrow("accuracy")),
                        speedKmh = cursor.getDouble(cursor.getColumnIndexOrThrow("speed_kmh"))
                    )
                )
            }
        }
        return com.ryda.app.core.sharing.RouteSimplifier.simplifyRoute(list)
    }

    fun getGpsPointsCount(rideId: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM gps_points WHERE ride_id = ?",
            arrayOf(rideId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }

    // --- MOTORCYCLE OPERATIONS ---

    fun getAllMotorcycles(): List<Motorcycle> {
        val list = mutableListOf<Motorcycle>()
        readableDatabase.rawQuery("SELECT * FROM motorcycles ORDER BY is_primary DESC, brand ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                val imgIdx = cursor.getColumnIndex("image_uri")
                val imgUri = if (imgIdx != -1) cursor.getString(imgIdx) else null
                list.add(
                    Motorcycle(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        brand = cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                        model = cursor.getString(cursor.getColumnIndexOrThrow("model")),
                        variant = cursor.getString(cursor.getColumnIndexOrThrow("variant")),
                        year = cursor.getInt(cursor.getColumnIndexOrThrow("year")),
                        engineCc = cursor.getInt(cursor.getColumnIndexOrThrow("engine_cc")),
                        nickname = cursor.getString(cursor.getColumnIndexOrThrow("nickname")),
                        isPrimary = cursor.getInt(cursor.getColumnIndexOrThrow("is_primary")) == 1,
                        imageUri = imgUri
                    )
                )
            }
        }
        return list
    }

    fun getPrimaryMotorcycle(): Motorcycle? {
        readableDatabase.rawQuery("SELECT * FROM motorcycles WHERE is_primary = 1 LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val imgIdx = cursor.getColumnIndex("image_uri")
                val imgUri = if (imgIdx != -1) cursor.getString(imgIdx) else null
                return Motorcycle(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    brand = cursor.getString(cursor.getColumnIndexOrThrow("brand")),
                    model = cursor.getString(cursor.getColumnIndexOrThrow("model")),
                    variant = cursor.getString(cursor.getColumnIndexOrThrow("variant")),
                    year = cursor.getInt(cursor.getColumnIndexOrThrow("year")),
                    engineCc = cursor.getInt(cursor.getColumnIndexOrThrow("engine_cc")),
                    nickname = cursor.getString(cursor.getColumnIndexOrThrow("nickname")),
                    isPrimary = true,
                    imageUri = imgUri
                )
            }
        }
        return getAllMotorcycles().firstOrNull()
    }

    fun insertMotorcycle(motorcycle: Motorcycle) {
        writableDatabase.use { db ->
            if (motorcycle.isPrimary) {
                db.execSQL("UPDATE motorcycles SET is_primary = 0")
            }
            val values = ContentValues().apply {
                put("id", motorcycle.id)
                put("brand", motorcycle.brand)
                put("model", motorcycle.model)
                put("variant", motorcycle.variant)
                put("year", motorcycle.year)
                put("engine_cc", motorcycle.engineCc)
                put("nickname", motorcycle.nickname)
                put("is_primary", if (motorcycle.isPrimary) 1 else 0)
                put("image_uri", motorcycle.imageUri)
            }
            db.insertWithOnConflict("motorcycles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun setPrimaryMotorcycle(bikeId: String) {
        writableDatabase.use { db ->
            db.execSQL("UPDATE motorcycles SET is_primary = 0")
            db.execSQL("UPDATE motorcycles SET is_primary = 1 WHERE id = '$bikeId'")
        }
    }

    fun deleteMotorcycle(bikeId: String) {
        writableDatabase.use { db ->
            db.delete("motorcycles", "id = ?", arrayOf(bikeId))
        }
    }

    fun updateMotorcycle(motorcycle: Motorcycle) {
        writableDatabase.use { db ->
            if (motorcycle.isPrimary) {
                db.execSQL("UPDATE motorcycles SET is_primary = 0")
            }
            val values = ContentValues().apply {
                put("brand", motorcycle.brand)
                put("model", motorcycle.model)
                put("variant", motorcycle.variant)
                put("year", motorcycle.year)
                put("engine_cc", motorcycle.engineCc)
                put("nickname", motorcycle.nickname)
                put("is_primary", if (motorcycle.isPrimary) 1 else 0)
                put("image_uri", motorcycle.imageUri)
            }
            db.update("motorcycles", values, "id = ?", arrayOf(motorcycle.id))
        }
    }

    // --- RIDER PROFILE OPERATIONS ---

    fun getRiderProfile(): RiderProfile {
        readableDatabase.rawQuery("SELECT * FROM rider_profile LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val avatarIdx = cursor.getColumnIndex("avatar_url")
                val avatar = if (avatarIdx != -1) cursor.getString(avatarIdx) ?: "" else ""
                val tagIdIdx = cursor.getColumnIndex("active_tag_id")
                val tagId = if (tagIdIdx != -1) cursor.getString(tagIdIdx) else null
                val tagNameIdx = cursor.getColumnIndex("active_tag_name")
                val tagName = if (tagNameIdx != -1) cursor.getString(tagNameIdx) else null

                return RiderProfile(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                    bio = cursor.getString(cursor.getColumnIndexOrThrow("bio")),
                    city = cursor.getString(cursor.getColumnIndexOrThrow("city")),
                    avatarUrl = avatar,
                    isProfilePublic = cursor.getInt(cursor.getColumnIndexOrThrow("is_profile_public")) == 1,
                    totalRides = cursor.getInt(cursor.getColumnIndexOrThrow("total_rides")),
                    totalDistanceKm = cursor.getDouble(cursor.getColumnIndexOrThrow("total_distance_km")),
                    totalTimeHours = cursor.getDouble(cursor.getColumnIndexOrThrow("total_time_hours")),
                    activeTagId = tagId,
                    activeTagName = tagName
                )
            }
        }
        return RiderProfile()
    }

    fun updateProfile(profile: RiderProfile) {
        writableDatabase.use { db ->
            val targetId = if (profile.id.isNotBlank()) profile.id else "rider_local_1"
            val values = ContentValues().apply {
                put("id", targetId)
                put("display_name", profile.displayName)
                put("bio", profile.bio)
                put("city", profile.city)
                put("avatar_url", profile.avatarUrl)
                put("is_profile_public", if (profile.isProfilePublic) 1 else 0)
                put("total_rides", profile.totalRides)
                put("total_distance_km", profile.totalDistanceKm)
                put("total_time_hours", profile.totalTimeHours)
                put("active_tag_id", profile.activeTagId)
                put("active_tag_name", profile.activeTagName)
            }
            val rows = db.update("rider_profile", values, null, null)
            if (rows == 0) {
                db.insertWithOnConflict("rider_profile", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    // --- USER ACHIEVEMENTS & TAGS OPERATIONS ---

    fun getUnlockedTagIds(userId: String = "rider_local_1"): Set<String> {
        val set = mutableSetOf<String>()
        readableDatabase.rawQuery(
            "SELECT tag_id FROM rider_tags WHERE user_id = ?",
            arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                set.add(cursor.getString(0))
            }
        }
        return set
    }

    fun unlockTag(userId: String = "rider_local_1", tagId: String) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("user_id", userId)
                put("tag_id", tagId)
                put("unlocked_at", System.currentTimeMillis().toString())
            }
            db.insertWithOnConflict("rider_tags", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun getUserAchievementProgress(userId: String = "rider_local_1"): Map<String, Pair<Double, Boolean>> {
        val map = mutableMapOf<String, Pair<Double, Boolean>>()
        readableDatabase.rawQuery(
            "SELECT achievement_id, progress_value, is_unlocked FROM user_achievements WHERE user_id = ?",
            arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val achId = cursor.getString(0)
                val prog = cursor.getDouble(1)
                val unlocked = cursor.getInt(2) == 1
                map[achId] = Pair(prog, unlocked)
            }
        }
        return map
    }

    fun saveUserAchievement(
        userId: String = "rider_local_1",
        achievementId: String,
        progressValue: Double,
        isUnlocked: Boolean
    ) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("user_id", userId)
                put("achievement_id", achievementId)
                put("progress_value", progressValue)
                put("is_unlocked", if (isUnlocked) 1 else 0)
                if (isUnlocked) {
                    put("unlocked_at", System.currentTimeMillis().toString())
                }
            }
            db.insertWithOnConflict("user_achievements", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun clearAllUserData() {
        writableDatabase.use { db ->
            db.delete("gps_points", null, null)
            db.delete("rides", null, null)
            db.delete("motorcycles", null, null)
            db.delete("rider_profile", null, null)
            val defaultBikeId = UUID.randomUUID().toString()
            db.execSQL(
                """
                INSERT INTO motorcycles (id, brand, model, variant, year, engine_cc, nickname, is_primary)
                VALUES ('$defaultBikeId', 'Royal Enfield', 'Himalayan 450', 'Kamet White', 2024, 452, 'The Sherpa', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO rider_profile (id, display_name, bio, city, is_profile_public, total_rides, total_distance_km, total_time_hours)
                VALUES ('rider_local_1', 'Ryda Rider', 'Motorcycle tourer', 'Bengaluru', 0, 0, 0, 0)
                """.trimIndent()
            )
        }
    }

    // --- COMMUNITY POST OPERATIONS ---

    fun getAllCommunityPosts(): List<CommunityPost> {
        val list = mutableListOf<CommunityPost>()
        try {
            readableDatabase.rawQuery("SELECT * FROM community_posts ORDER BY id DESC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val rideId = cursor.getString(cursor.getColumnIndexOrThrow("ride_id"))
                    val rideTitle = cursor.getString(cursor.getColumnIndexOrThrow("ride_title"))
                    val telemetry = if (rideId.isNotBlank()) {
                        RideTelemetrySnippet(
                            id = rideId,
                            title = rideTitle,
                            totalDistanceKm = cursor.getDouble(cursor.getColumnIndexOrThrow("total_distance_km")),
                            movingTimeSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("moving_time_seconds")),
                            avgSpeedKmh = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_speed_kmh")),
                            motorcycleName = cursor.getString(cursor.getColumnIndexOrThrow("motorcycle_name"))
                        )
                    } else null

                    list.add(
                        CommunityPost(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            authorId = cursor.getInt(cursor.getColumnIndexOrThrow("author_id")),
                            authorUsername = cursor.getString(cursor.getColumnIndexOrThrow("author_username")),
                            authorDisplayName = cursor.getString(cursor.getColumnIndexOrThrow("author_display_name")),
                            authorAvatarUrl = cursor.getString(cursor.getColumnIndexOrThrow("author_avatar_url")),
                            authorCity = cursor.getString(cursor.getColumnIndexOrThrow("author_city")),
                            postType = cursor.getString(cursor.getColumnIndexOrThrow("post_type")),
                            caption = cursor.getString(cursor.getColumnIndexOrThrow("caption")),
                            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("image_url")),
                            createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                            rideTelemetry = telemetry,
                            likesCount = cursor.getInt(cursor.getColumnIndexOrThrow("likes_count")),
                            commentsCount = cursor.getInt(cursor.getColumnIndexOrThrow("comments_count")),
                            isLiked = cursor.getInt(cursor.getColumnIndexOrThrow("is_liked")) == 1,
                            isFollowingAuthor = cursor.getInt(cursor.getColumnIndexOrThrow("is_following_author")) == 1
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun insertCommunityPost(post: CommunityPost) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("id", post.id)
                put("author_id", post.authorId)
                put("author_username", post.authorUsername)
                put("author_display_name", post.authorDisplayName)
                put("author_avatar_url", post.authorAvatarUrl)
                put("author_city", post.authorCity)
                put("post_type", post.postType)
                put("caption", post.caption)
                put("image_url", post.imageUrl)
                put("created_at", post.createdAt)
                put("ride_id", post.rideTelemetry?.id ?: "")
                put("ride_title", post.rideTelemetry?.title ?: "")
                put("total_distance_km", post.rideTelemetry?.totalDistanceKm ?: 0.0)
                put("moving_time_seconds", post.rideTelemetry?.movingTimeSeconds ?: 0L)
                put("avg_speed_kmh", post.rideTelemetry?.avgSpeedKmh ?: 0.0)
                put("motorcycle_name", post.rideTelemetry?.motorcycleName ?: "Motorcycle")
                put("likes_count", post.likesCount)
                put("comments_count", post.commentsCount)
                put("is_liked", if (post.isLiked) 1 else 0)
                put("is_following_author", if (post.isFollowingAuthor) 1 else 0)
            }
            db.insertWithOnConflict("community_posts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // --- CLUB & RALLY OPERATIONS ---

    fun getAllClubs(cityFilter: String? = null): List<Club> {
        val list = mutableListOf<Club>()
        try {
            val query = if (cityFilter.isNullOrBlank() || cityFilter == "All") {
                "SELECT * FROM clubs ORDER BY is_verified DESC, name ASC"
            } else {
                "SELECT * FROM clubs WHERE city = '$cityFilter' ORDER BY is_verified DESC, name ASC"
            }
            readableDatabase.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val myRoleIdx = cursor.getColumnIndex("my_role")
                    val myRoleVal = if (myRoleIdx != -1 && !cursor.isNull(myRoleIdx)) cursor.getString(myRoleIdx) else null
                    list.add(
                        Club(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            slug = cursor.getString(cursor.getColumnIndexOrThrow("slug")),
                            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                            city = cursor.getString(cursor.getColumnIndexOrThrow("city")),
                            bannerUrl = cursor.getString(cursor.getColumnIndexOrThrow("banner_url")),
                            logoUrl = cursor.getString(cursor.getColumnIndexOrThrow("logo_url")),
                            isVerified = cursor.getInt(cursor.getColumnIndexOrThrow("is_verified")) == 1,
                            policy = cursor.getString(cursor.getColumnIndexOrThrow("policy")),
                            totalMileageKm = cursor.getDouble(cursor.getColumnIndexOrThrow("total_mileage_km")),
                            membersCount = cursor.getInt(cursor.getColumnIndexOrThrow("members_count")),
                            isMember = cursor.getInt(cursor.getColumnIndexOrThrow("is_member")) == 1,
                            myRole = myRoleVal
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun insertClub(club: Club) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("id", club.id)
                put("name", club.name)
                put("slug", club.slug)
                put("description", club.description)
                put("city", club.city)
                put("banner_url", club.bannerUrl)
                put("logo_url", club.logoUrl)
                put("is_verified", if (club.isVerified) 1 else 0)
                put("policy", club.policy)
                put("total_mileage_km", club.totalMileageKm)
                put("members_count", club.membersCount)
                put("is_member", if (club.isMember) 1 else 0)
                put("my_role", club.myRole)
            }
            db.insertWithOnConflict("clubs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun getAllRallyEvents(): List<RidingEvent> {
        val list = mutableListOf<RidingEvent>()
        try {
            readableDatabase.rawQuery("SELECT * FROM rally_events ORDER BY start_time ASC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val myRsvpIdx = cursor.getColumnIndex("my_rsvp")
                    val myRsvpVal = if (myRsvpIdx != -1) cursor.getString(myRsvpIdx) else null
                    list.add(
                        RidingEvent(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            clubId = cursor.getString(cursor.getColumnIndexOrThrow("club_id")),
                            clubName = cursor.getString(cursor.getColumnIndexOrThrow("club_name")),
                            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                            meetingPoint = cursor.getString(cursor.getColumnIndexOrThrow("meeting_point")),
                            destination = cursor.getString(cursor.getColumnIndexOrThrow("destination")),
                            startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time")),
                            riderLimit = cursor.getInt(cursor.getColumnIndexOrThrow("rider_limit")),
                            pace = cursor.getString(cursor.getColumnIndexOrThrow("pace")),
                            distanceKm = cursor.getDouble(cursor.getColumnIndexOrThrow("distance_km")),
                            isOfficial = cursor.getInt(cursor.getColumnIndexOrThrow("is_official")) == 1,
                            confirmedCount = cursor.getInt(cursor.getColumnIndexOrThrow("confirmed_count")),
                            interestedCount = cursor.getInt(cursor.getColumnIndexOrThrow("interested_count")),
                            myRsvp = myRsvpVal
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun insertRallyEvent(event: RidingEvent) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put("id", event.id)
                put("club_id", event.clubId)
                put("club_name", event.clubName)
                put("title", event.title)
                put("description", event.description)
                put("meeting_point", event.meetingPoint)
                put("destination", event.destination)
                put("start_time", event.startTime)
                put("rider_limit", event.riderLimit)
                put("pace", event.pace)
                put("distance_km", event.distanceKm)
                put("is_official", if (event.isOfficial) 1 else 0)
                put("confirmed_count", event.confirmedCount)
                put("interested_count", event.interestedCount)
                put("my_rsvp", event.myRsvp)
            }
            db.insertWithOnConflict("rally_events", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}

package com.swiperf.app.data.session

import android.content.Context
import androidx.room.*

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val clusterCount: Int,
    val traceCount: Int,
    val jsonData: String // Full session JSON
)

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val key: String, // always "current"
    val activeSessionId: String?,
    val activeClusterId: String?,
    val stateJson: String? // Serialized current state (verdicts, filters, slider positions, compare state)
)

@Dao
interface SessionDao {
    @Query("SELECT id, name, createdAt, updatedAt, clusterCount, traceCount FROM sessions ORDER BY updatedAt DESC")
    suspend fun listSessions(): List<SessionMeta>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int

    // App state
    @Query("SELECT * FROM app_state WHERE `key` = 'current'")
    suspend fun getAppState(): AppStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppState(state: AppStateEntity)

    @Query("DELETE FROM app_state")
    suspend fun clearAppState()
}

data class SessionMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val clusterCount: Int,
    val traceCount: Int
)

@Database(entities = [SessionEntity::class, AppStateEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "swiperf.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

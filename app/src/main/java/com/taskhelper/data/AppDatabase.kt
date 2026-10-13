package com.taskhelper

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val pkg: String,
    val hour: Int,
    val minute: Int,
    val durationMin: Int,
    val repeat: String,   // 每天 / 工作日 / 周末 / 仅一次
    val remind: Boolean,
    val enabled: Boolean
)

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: String,
    val appName: String,
    val durationMin: Int,
    val success: Boolean,
    val reason: String = ""
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY hour, minute")
    fun all(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun allList(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE enabled = 1")
    suspend fun enabledList(): List<TaskEntity>

    @Insert
    suspend fun insert(t: TaskEntity): Long

    @Update
    suspend fun update(t: TaskEntity)

    @Delete
    suspend fun delete(t: TaskEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY id DESC LIMIT 50")
    fun recent(): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(l: LogEntity)

    /** 仅保留最近 100 条，防止日志表无限增长。 */
    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT 100)")
    suspend fun trim()
}

@Database(entities = [TaskEntity::class, LogEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 数据库迁移策略：
         * - 注册显式迁移（MIGRATION_1_2 等），保证版本升级时表结构平滑演进；
         * - fallbackToDestructiveMigration() 作为兜底：当某个新版本缺少对应迁移时，
         *   直接重建数据库而非抛异常崩溃（代价是旧数据清空，对工具类 App 可接受）。
         * 注意：每次修改 @Entity 字段后，必须新增对应的 MIGRATION_n_(n+1)，
         * 否则会走兜底重建。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 当前 1→2 无结构变更，此处为安全占位。
                // 未来若给 tasks/logs 加列，请在此执行 ALTER TABLE。
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 2→3：日志表新增 reason 列，记录执行失败原因
                db.execSQL("ALTER TABLE logs ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taskhelper.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

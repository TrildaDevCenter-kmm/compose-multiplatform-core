/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.integration.multiplatformtestapp.test

import androidx.room.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class QueryTest(private val driver: SQLiteDriver) : BaseQueryTest() {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    override fun getRoomDatabase(): SampleDatabase {
        return Room.inMemoryDatabaseBuilder<SampleDatabase>(context = instrumentation.targetContext)
            .setDriver(driver)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    companion object {
        @JvmStatic
        @Parameters(name = "driver={0}")
        fun drivers() = arrayOf(BundledSQLiteDriver(), AndroidSQLiteDriver())
    }
}
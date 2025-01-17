/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.paging

import androidx.paging.RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH
import androidx.paging.RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
@RunWith(JUnit4::class)
class ListenableFutureRemoteMediatorTest {
    @Test
    fun initializeFuture() = runTest {
        val remoteMediator =
            object : ListenableFutureRemoteMediator<Int, Int>() {
                override fun loadFuture(
                    loadType: LoadType,
                    state: PagingState<Int, Int>
                ): ListenableFuture<MediatorResult> {
                    fail("Unexpected call")
                }

                override fun initializeFuture() = Futures.immediateFuture(SKIP_INITIAL_REFRESH)
            }

        assertEquals(SKIP_INITIAL_REFRESH, remoteMediator.initialize())
    }

    @Test
    fun initializeFutureDefault() = runTest {
        val remoteMediator =
            object : ListenableFutureRemoteMediator<Int, Int>() {
                override fun loadFuture(
                    loadType: LoadType,
                    state: PagingState<Int, Int>
                ): ListenableFuture<MediatorResult> {
                    fail("Unexpected call")
                }
            }

        assertEquals(LAUNCH_INITIAL_REFRESH, remoteMediator.initialize())
    }
}

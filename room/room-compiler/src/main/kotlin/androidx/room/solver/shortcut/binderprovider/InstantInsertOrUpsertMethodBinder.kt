/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.room.solver.shortcut.binderprovider

import androidx.room.compiler.processing.XType
import androidx.room.processor.Context
import androidx.room.solver.shortcut.binder.InsertOrUpsertMethodBinder
import androidx.room.solver.shortcut.binder.InstantInsertOrUpsertMethodBinder
import androidx.room.vo.ShortcutQueryParameter

/** Provider for instant (blocking) insert or upsert method binder. */
class InstantInsertOrUpsertMethodBinderProvider(private val context: Context) :
    InsertOrUpsertMethodBinderProvider {

    override fun matches(declared: XType) = true

    override fun provide(
        declared: XType,
        params: List<ShortcutQueryParameter>,
        forUpsert: Boolean
    ): InsertOrUpsertMethodBinder {
        return InstantInsertOrUpsertMethodBinder(
            if (forUpsert) {
                context.typeAdapterStore.findUpsertAdapter(declared, params)
            } else {
                context.typeAdapterStore.findInsertAdapter(declared, params)
            }
        )
    }
}
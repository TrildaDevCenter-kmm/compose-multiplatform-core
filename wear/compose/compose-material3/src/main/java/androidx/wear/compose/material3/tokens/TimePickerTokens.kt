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

package androidx.wear.compose.material3.tokens

internal object TimePickerTokens {
    val SelectedPickerContentColor = ColorSchemeKeyTokens.OnBackground
    val UnselectedPickerContentColor = ColorSchemeKeyTokens.SecondaryDim
    val SeparatorColor = ColorSchemeKeyTokens.OnSurfaceVariant
    val PickerLabelColor = ColorSchemeKeyTokens.Primary
    val ConfirmButtonContentColor = ColorSchemeKeyTokens.OnPrimary
    val ConfirmButtonContainerColor = ColorSchemeKeyTokens.PrimaryDim

    val PickerLabelLargeTypography = TypographyKeyTokens.TitleLarge
    val PickerLabelTypography = TypographyKeyTokens.TitleMedium
    val PickerContentLargeTypography = TypographyKeyTokens.NumeralMedium
    val PickerContentTypography = TypographyKeyTokens.NumeralSmall
}
/*
 * Copyright 2022 QuiltMC
 * Modified by the Steam 'n' Rails (Railways) team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.railwayteam.railways.base.datafixerapi;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.NoSuchElementException;

@ApiStatus.Internal
public final class DataFixesInternalsImpl extends DataFixesInternals {
    private final @NotNull Schema latestVanillaSchema;

    private DataFixerEntry dataFixer;

    public DataFixesInternalsImpl(@NotNull Schema latestVanillaSchema) {
        this.latestVanillaSchema = latestVanillaSchema;

        this.dataFixer = null;
    }

    @Override
    public void registerFixer(@Range(from = 0, to = Integer.MAX_VALUE) int currentVersion,
                              @NotNull DataFixer dataFixer) {
        if (this.dataFixer != null) {
            throw new IllegalArgumentException("Railways already has a registered data fixer");
        }

        this.dataFixer = new DataFixerEntry(dataFixer, currentVersion);
    }

    @Override
    public @Nullable DataFixerEntry getFixerEntry() {
        return dataFixer;
    }

    @Override
    public @NotNull Schema createBaseSchema() {
        // Validate parent schema before attempting to create a NamespacedSchema.
        // NamespacedSchema delegates type building to parent, which can throw NoSuchElementException
        // if the parent schema is missing expected type definitions.
        if (this.latestVanillaSchema == null) {
            throw new IllegalStateException(
                "[Railways DFU] Cannot create base schema: latestVanillaSchema is null. " +
                "DataFixer may not have been properly initialized by NeoForge."
            );
        }

        try {
            return new NamespacedSchema(0, this.latestVanillaSchema);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(
                "[Railways DFU] Failed to create base schema: parent Minecraft schema is missing " +
                "expected type definitions. This may indicate a mod conflict or incomplete DFU initialization. " +
                "Exception: " + e.getMessage(),
                e
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                "[Railways DFU] Unexpected error during base schema creation. " +
                "This is likely a mod compatibility issue. Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public @NotNull CompoundTag updateWithAllFixers(@NotNull DataFixTypes dataFixTypes, @NotNull CompoundTag compound) {
        var current = new Dynamic<>(NbtOps.INSTANCE, compound);

        if (dataFixer != null) {
            int modDataVersion = DataFixesInternals.getModDataVersion(compound);
            current = dataFixTypes.update(dataFixer.dataFixer(), current, modDataVersion, dataFixer.currentVersion());
        }

        return (CompoundTag) current.getValue();
    }

    @Override
    public @NotNull CompoundTag addModDataVersions(@NotNull CompoundTag compound) {
        if (dataFixer != null)
            compound.putInt("Railways_DataVersion", dataFixer.currentVersion());

        return compound;
    }
}
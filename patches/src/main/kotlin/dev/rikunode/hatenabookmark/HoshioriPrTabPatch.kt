package dev.rikunode.hatenabookmark

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val ADAPTER = "Lcom/hatena/android/bookmark/presentation/fragment/entrylist/TopFragment\$Adapter;"
private const val TOP = "Lcom/hatena/android/bookmark/presentation/fragment/entrylist/TopFragment;"
private const val PR = "Ldev/rikunode/settings/HoshioriPrTab;"

/** Append one page to the stock pager, leaving the original enum and saved tab order unchanged. */
@Suppress("unused")
val hoshioriPrTabPatch = bytecodePatch(
    name = "PR article tab",
    description = "Optionally show received PR articles in a separate home tab.",
    default = true,
) {
    compatibleWith(Compatibility(
        name = "Hatena Bookmark", packageName = "com.hatena.android.bookmark",
        apkFileType = ApkFileType.APK, appIconColor = 0x2A9BD6,
        targets = listOf(AppTarget(version = "5.80", versionCode = 58000)),
    ))
    dependsOn(hideNativePrEntriesPatch)
    execute {
        fun guarded(owner: String, name: String, params: Int, registers: Int, size: Int) =
            classDefBy(owner).methods.singleOrNull {
                it.name == name && it.parameterTypes.size == params &&
                    it.implementation?.registerCount == registers &&
                    it.implementation?.instructions?.sumOf { i -> i.codeUnits } == size
            } ?: throw PatchException("Hatena 5.80 PR pager guard failed: $name")

        val init = guarded(ADAPTER, "<init>", 6, 8, 48)
        val count = guarded(ADAPTER, "getCount", 0, 2, 6)
        val item = guarded(ADAPTER, "getItem", 1, 4, 104)
        val title = guarded(ADAPTER, "getPageTitle", 1, 4, 64)
        val resume = guarded(TOP, "onResume", 0, 4, 74)
        fun at(method: com.android.tools.smali.dexlib2.iface.Method, offset: Int, opcode: Opcode): Int {
            var pc = 0
            for ((index, instruction) in method.implementation!!.instructions.withIndex()) {
                if (pc == offset && instruction.opcode == opcode) return index
                pc += instruction.codeUnits
            }
            throw PatchException("Hatena 5.80 PR pager call-site guard failed: ${method.name}")
        }
        val initIndex = at(init, 0x2f, Opcode.RETURN_VOID)
        val countIndex = at(count, 5, Opcode.RETURN)
        val resumeIndex = at(resume, 0x2b, Opcode.IF_NEZ)
        require(item.implementation!!.instructions.first().opcode == Opcode.SGET_OBJECT)
        require(title.implementation!!.instructions.first().opcode == Opcode.SGET_OBJECT)
        val owner = mutableClassDefBy(ADAPTER)
        fun find(name: String, params: Int) = owner.methods.single {
            it.name == name && it.parameterTypes.size == params
        }
        find("<init>", 6).addInstructions(initIndex, """
            iget-object v0, p0, ${ADAPTER}->context:Landroid/content/Context;
            invoke-static { p0, v0 }, ${PR}->initAdapter(Ljava/lang/Object;Landroid/content/Context;)V
        """.trimIndent())
        find("getCount", 0).addInstructions(countIndex, """
            invoke-static { p0, v0 }, ${PR}->count(Ljava/lang/Object;I)I
            move-result v0
        """.trimIndent())
        find("getItem", 1).addInstructionsWithLabels(0, """
            invoke-static { p0, p1 }, ${PR}->isPrPage(Ljava/lang/Object;I)Z
            move-result v0
            if-eqz v0, :original_page
            new-instance v0, Ldev/rikunode/settings/HoshioriPrFragment;
            invoke-direct { v0 }, Ldev/rikunode/settings/HoshioriPrFragment;-><init>()V
            return-object v0
            :original_page
            nop
        """.trimIndent())
        find("getPageTitle", 1).addInstructionsWithLabels(0, """
            invoke-static { p0, p1 }, ${PR}->isPrPage(Ljava/lang/Object;I)Z
            move-result v0
            if-eqz v0, :original_title
            const-string v0, "PR"
            return-object v0
            :original_title
            nop
        """.trimIndent())
        // Reuse the host's existing tab-order-change navigation path.
        mutableClassDefBy(TOP).methods.single {
            it.name == "onResume" && it.parameterTypes.isEmpty()
        }.addInstructionsWithLabels(resumeIndex, """
            invoke-static { p0 }, ${PR}->configChanged(Ljava/lang/Object;)Z
            move-result v2
            if-eqz v2, :same_config
            const/4 v1, 0x0
            :same_config
            nop
        """.trimIndent())
    }
}

package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t44（ADR-29）：prelude 去枚举化验收——内建类型与用户代码同构（struct + 联合）。
 *
 * 覆盖：构造形态（构造调用返 struct 名、实参核对、Channel 默认容量）、空构造子单例
 * （E-NULLARY-PAREN 实测文本）、联合形态（别名拓宽、when 穷尽性走 t41 联合通道）、
 * 用户声明优先（P-7）、HIR/JSON 形态。
 */
class PreludeDeEnumTest {

    // t138（R-23 B1）：prelude 虚拟层 retire——编译挂 stdlib 快照（与 CLI 默认挂载同构；
    // 虚拟 struct/别名数据源已换 stdlib 源——P-7 用户同名优先口径不变）
    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "prelude44.subl")
        val (hir, bag) = compile(pr.file, pr.notes, scriptMode = false, mounted = stdlibMounts)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return hir to bag
    }

    private fun errCodes(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun allCodes(bag: DiagBag) = bag.diags.map { it.code }
    private fun errMsgs(bag: DiagBag) =
        bag.diags.filter { it.severity == Severity.ERROR }.joinToString(" | ") { it.code + ":" + it.message }

    private fun varType(hir: HirFile, name: String): String? = hir.entries.mapNotNull {
        (it as? HirStmtEntry)?.stmt as? HirVarStmt
    }.firstOrNull { it.name == name }?.type?.let { (it as? HirNamedType)?.name }

    // ============================ 1 构造形态（struct 化） ============================

    @Test
    fun someCallTypedAsStructNotEnum() {
        val (hir, bag) = script("var x = Some(1)")
        assertFalse(bag.hasError, "Some(1) 经虚拟 struct 通道核对通过。实际: " + errMsgs(bag))
        assertEquals("Some", varType(hir, "x"), "构造调用返回 **struct 名** Some（旧枚举口径返 Optional）")
    }

    @Test
    fun bareNoneIsSingletonValue() {
        val (hir, bag) = script("var x = None")
        assertFalse(bag.hasError, "裸 None 取单例值。实际: " + errMsgs(bag))
        assertEquals("None", varType(hir, "x"), "裸空构造子 : None（旧口径为合成内建类型）")
    }

    @Test
    fun consCallTypedAsStructAndNestedArgsChecked() {
        val (hir, bag) = script("var xs = Cons(1, Nil)")
        assertFalse(bag.hasError, "Cons(1, Nil) 嵌套构造核对通过。实际: " + errMsgs(bag))
        assertEquals("Cons", varType(hir, "xs"))
    }

    @Test
    fun ctorArityChecked() {
        val (_, bag) = script("var x = Some(1, 2)")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "Some 字段数 1，多给实参报错。实际: " + errMsgs(bag))
    }

    @Test
    fun okErrTypedAsOwnStruct() {
        val (hir, bag) = script("var o = Ok(1)\nvar e = Err(\"err\")")
        assertFalse(bag.hasError, "Ok/Err 各自 struct 形态构造。实际: " + errMsgs(bag))
        assertEquals("Ok", varType(hir, "o"))
        assertEquals("Err", varType(hir, "e"))
    }

    @Test
    fun setConsAndMapConsTypedAsOwnStruct() {
        val (hir, bag) = script("var s = SetCons(1, SetNil)\nvar m = MapCons(1, \"one\", MapNil)")
        assertFalse(bag.hasError, "SetCons/MapCons 嵌套构造核对通过。实际: " + errMsgs(bag))
        assertEquals("SetCons", varType(hir, "s"))
        assertEquals("MapCons", varType(hir, "m"))
    }

    @Test
    fun channelCapDefaultZeroOmittable() {
        val (hir, bag) = script("var ch = Channel<Nat>()")
        assertFalse(bag.hasError, "Channel<Nat>() 容量可省（默认 0=无缓冲，旧实现 new Channel(0)）。实际: " + errMsgs(bag))
        assertEquals("Channel", varType(hir, "ch"))
    }

    @Test
    fun channelCapProvidedStillFine() {
        val (hir, bag) = script("var ch = Channel<Nat>(4)")
        assertFalse(bag.hasError, "Channel<Nat>(4) 带容量构造。实际: " + errMsgs(bag))
        assertEquals("Channel", varType(hir, "ch"))
    }

    // ============================ 2 空构造子单例（E-NULLARY-PAREN 实测） ============================

    @Test
    fun noneParenCallRejectedWithVerdictText() {
        val (_, bag) = script("var x = None()")
        assertTrue(errCodes(bag).contains("E-NULLARY-PAREN"), "None() → E-NULLARY-PAREN")
        assertTrue(errMsgs(bag).contains(
            "空构造子 `None` 不能用 () 构造，请直接写 `None`（空构造子全局唯一）"), "裁决文案逐字。实际: " + errMsgs(bag))
    }

    @Test
    fun nilParenCallRejected() {
        val (_, bag) = script("var x = Nil()")
        assertTrue(errCodes(bag).contains("E-NULLARY-PAREN"), "Nil() → E-NULLARY-PAREN")
    }

    @Test
    fun setNilMapNilParenCallRejected() {
        val (_, bag1) = script("var x = SetNil()")
        assertTrue(errCodes(bag1).contains("E-NULLARY-PAREN"), "SetNil() → E-NULLARY-PAREN")
        val (_, bag2) = script("var x = MapNil()")
        assertTrue(errCodes(bag2).contains("E-NULLARY-PAREN"), "MapNil() → E-NULLARY-PAREN")
    }

    @Test
    fun nullaryParensPatternAlsoRejected() {
        val (_, bag) = script("fun f(o: Optional[Nat]): Nat = when (o) { None() -> 0 ; is Some(v) -> v }")
        assertTrue(errCodes(bag).contains("E-NULLARY-PAREN"), "模式位 None() → E-NULLARY-PAREN")
    }

    // ============================ 3 联合形态（别名/拓宽/穷尽性） ============================

    @Test
    fun memberWidensToPreludeUnion() {
        val (_, bag) = script("var x: Optional[Nat] = Some(1)")
        assertFalse(bag.hasError, "成员 Some[Nat] 单向拓宽到联合别名 Optional[Nat]。实际: " + errMsgs(bag))
    }

    @Test
    fun unionToMemberIsIllegal() {
        val (_, bag) = script("var x: Optional[Nat] = Some(1)\nvar y: Some[Nat] = x")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "联合→成员非法（需 is 判定）。实际: " + errMsgs(bag))
    }

    @Test
    fun optionalWhenExhaustiveViaUnionChannel() {
        val (hir, bag) = script("fun f(o: Optional[Nat]): Nat = when (o) { is Some(v) -> v ; is None -> 0 }")
        assertFalse(bag.hasError, "Some/None 联合覆盖 → 无错。实际: " + errMsgs(bag))
        assertFalse(allCodes(bag).contains("W-NON-EXHAUSTIVE"))
        val f = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }.first()
        assertTrue((f.body as HirWhen).exhaustive, "穷尽性标记经联合通道置真")
    }

    @Test
    fun optionalWhenMissingMemberReports() {
        val (_, bag) = script("fun f(o: Optional[Nat]): Nat = when (o) { is Some(v) -> v }")
        assertTrue(errCodes(bag).contains("E-NON-SEALED-MATCH"), "缺 None → E-NON-SEALED-MATCH")
        assertTrue(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "缺 None → W-NON-EXHAUSTIVE")
    }

    @Test
    fun listSetMapResultWhenExhaustiveviaUnion() {
        val srcs = listOf(
            "fun g(xs: List[Nat]): Nat = when (xs) { Nil -> 0 ; is Cons(h, _) -> 1 }",
            "fun g(s: Set[Nat]): Nat = when (s) { SetNil -> 0 ; is SetCons(h, _) -> 1 }",
            "fun g(m: Map[Nat, Str]): Nat = when (m) { MapNil -> 0 ; is MapCons(k, v, _) -> 1 }",
            "fun g(r: Result[Nat, Str]): Nat = when (r) { is Ok(v) -> v ; is Err(_) -> 0 }",
        )
        srcs.forEachIndexed { i, src ->
            val (_, bag) = script(src)
            assertFalse(bag.hasError, "第 " + i + " 组联合穷尽。实际: " + errMsgs(bag))
            assertFalse(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "第 " + i + " 组不落 W-NON-EXHAUSTIVE")
        }
    }

    @Test
    fun patternFieldTypesInstantiatedFromSubjectArgs() {
        val (_, bag) = script("fun f(o: Optional[Str]): Nat = when (o) { is Some(v) -> 1 ; is None -> 0 }\n" +
            "fun g(o: Optional[Nat]): Nat = when (o) { is Some(v) -> v ; is None -> 0 }")
        assertFalse(bag.hasError, "模式字段类型按主题实参实例化：Str 位的 v 不可作 Nat 返回（破坏用例见 negativeInstantiation）。实际: " + errMsgs(bag))
    }

    @Test
    fun negativeInstantiation() {
        val (_, bag) = script("fun f(o: Optional[Str]): Nat = when (o) { is Some(v) -> v ; is None -> 0 }")
        assertTrue(errCodes(bag).contains("E-TYPE-MISMATCH"),
            "v : Str（主题 Optional[Str] 代入）不可作 Nat 返回 → E-TYPE-MISMATCH。实际: " + errMsgs(bag))
    }

    // ============================ 4 用户声明优先（P-7） ============================

    @Test
    fun userStructShadowsPreludeCtor() {
        val (hir, bag) = script("struct None(x: Nat)\nvar a = None(1)")
        assertFalse(bag.hasError, "用户 struct None 覆盖 prelude 空构造子。实际: " + errMsgs(bag))
        assertEquals("None", varType(hir, "a"))
    }

    @Test
    fun userStructShadowsPreludeCtorTypedCtor() {
        val (hir, bag) = script("struct Some(x: Str)\nvar a = Some(\"s\")")
        assertFalse(bag.hasError, "用户 struct Some（Str 字段）覆盖 prelude Some[T]。实际: " + errMsgs(bag))
        assertEquals("Some", varType(hir, "a"))
    }

    @Test
    fun preludeAliasUserOverrideP7() {
        // t47（ADR-31 R-26）：Optional/List/Result/Set/Map 已让出 BUILTIN_TYPES——容器类型的唯一权威是
        // prelude 虚拟联合别名，用户同名别名按 P-7 直接覆盖（不再落「与既有类型重名」E-DUP-DECL）。
        // 虚拟 **struct** 名（None/Some/Nil…）从来无此占位：用户同名 struct 直接覆盖（见上面两条
        // userStruct* 测试）。
        val (hir, bag) = script("type Optional[T] = Nat\nvar x: Optional[Nat] = 0")
        assertFalse(bag.hasError, "用户别名 Optional 覆盖 prelude 虚拟联合别名。实际: " + errMsgs(bag))
    }

    // ============================ 5 HIR/JSON 形态 ============================

    @Test
    fun inferredStructTypeEmitsJson() {
        val (hir, _) = script("var x = Some(1)\nvar o = Ok(2)")
        val json = HirJson.emit(hir, DiagBag(), "prelude44.subl")
        assertTrue(json.contains("\"name\": \"Some\""), "推导类型 Some 落 JSON")
        assertTrue(json.contains("\"name\": \"Ok\""), "推导类型 Ok 落 JSON")
    }

    @Test
    fun preludeVirtualDeclsNotEmittedAsFileEntries() {
        val (hir, _) = script("var x = Some(1)")
        val json = HirJson.emit(hir, DiagBag(), "prelude44.subl")
        // 虚拟 struct/别名不是文件条目：Some 作为构造子符号可引用，但不产出 HirStructDecl/HirTypeAlias 条目
        assertTrue(hir.entries.none { it.toString().contains("HirStructDecl(name=None)") }, "不落虚拟 struct 声明条目")
        assertFalse(json.contains("\"kind\": \"HirTypeAlias\""), "prelude 联合别名不落 HIR 声明产物")
    }
}

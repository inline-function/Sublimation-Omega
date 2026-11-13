package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.TypeParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t140（stdlib B2.2 / HKT 特质族·接收者形态）: 特质方法接收者位 + 满足面双通道 + 快照 trait 面
 * 的回归门（用户裁决：特质方法统一接收者形态 fun F[A].map[A,B](f: (A)=>B): F[B]）。
 *
 * - 接收者形态规范：特质成员建接收者位；接收者头里的 kind 形参（F）查名认 theory 参数——
 *   消 W-EXT-OBSOLETE 假阳（旧版 lookupType(F) 落空误报「疑似拼写错误」）；
 * - 满足面双通道：接收者形态方法 ← 同形扩展函数（satisfiesCheck 形状恒等，P-8 迁移层）；
 *   无接收者方法（pure）← 自由函数（今天只匹扩展函数——实缺口）；
 * - 快照 trait 面（t134 漏了）：ModuleSnapshot 加 traits 面——限定形态 traits.Functor 引用 +
 *   父域 traitDecls 注入（裸名链不通，R-22）；
 * - traverse 边界：M[_]: Applicative 单子约束 V1 不可为界（TS-24/E-TC-BOUNDARY 实测落红）
 *   ⇒ Traversable 本体挂账（卷 §10.2，不准硬砸）。 */
class T140StdlibTraitsTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = true): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t140.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    private fun stdlibUnits() = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)

    // -------------------- 门 1：stdlib 特质模块落位（快照 trait 面） --------------------

    /** 五特质落 stdlib collection/traits 件；成员形态 = 接收者形态（map/ap/flatMap/fold/traverse）
     *  + pure 无接收者（自由函数通道）。 */
    @Test fun stdlib特质族落位() {
        val (su, diag) = stdlibUnits()
        assertEquals(false, diag.hasError, "stdlib 全家绿（traits 件在内）: " +
            diag.diags.filter { it.severity == Severity.ERROR }.joinToString { it.message })
        val traitsSnap = su.filter { it.snapshot.stdlib }.firstOrNull { it.alias == "traits" }
        assertNotNull(traitsSnap, "traits 件入 stdlib 挂载树（别名 traits）")
        val names = traitsSnap.snapshot.traits.keys
        listOf("Functor", "Applicative", "Monad", "Foldable", "Traversable").forEach {
            assertTrue(it in names, "特质 $it 落 traits 件")
        }
        val functor = traitsSnap.snapshot.traits["Functor"]?.decl
        assertNotNull(functor, "Functor 声明在位")
        val map = functor.members.first { it.name == "map" }
        assertTrue(map.receiver is NamedType && (map.receiver as NamedType).name == "F",
            "map 建接收者位 F[A]（用户裁决形态）")
        assertEquals(listOf("A", "B"), map.theory.filterIsInstance<TypeParam>().map { it.name },
            "map 理论参数 [A, B]")
        val applicative = traitsSnap.snapshot.traits["Applicative"]?.decl
        assertNotNull(applicative)
        val pure = applicative.members.first { it.name == "pure" }
        assertEquals(null, pure.receiver, "pure 无接收者（自由函数满足通道）")
        assertEquals(true, applicative.parents.any { (it as? NamedType)?.name == "Functor" },
            "Applicative : Functor（继承闭包 TS-8）")
        val monad = traitsSnap.snapshot.traits["Monad"]?.decl
        assertNotNull(monad)
        assertEquals(true, monad.members.any { it.name == "flatMap" } && monad.members.none { it.name == "pure" },
            "Monad 继承 Applicative（pure 经继承闭包供给，不重复声明）")
        // traverse 边界形态：M 无界（V1 约束面不可达——本体挂账）
        val traversable = traitsSnap.snapshot.traits["Traversable"]?.decl
        assertNotNull(traversable)
        val traverse = traversable.members.first { it.name == "traverse" }
        val mParam = traverse.theory.filterIsInstance<TypeParam>().first { it.name == "M" }
        assertEquals(null, mParam.constraint, "traverse 的 M 走无界形态（单子约束 V1 挂账，不准硬砸）")
    }

    /** 容器实例 auto-impl：List/Optional/Set 的 map-ap-flatMap-fold/pure **扩展函数**
     *  （声明即满足 ST-3/ST-9；pure 的自由函数实例在 stdlib **无裸名落位**——R-23 裸名链
     *  纪律：特质实例名不入用户裸名面，自由函数通道是用户模块级能力，见语言面门）。 */
    @Test fun 容器实例声明即满足() {
        val (su, diag) = stdlibUnits()
        assertEquals(false, diag.hasError, "stdlib 全家绿: " +
            diag.diags.filter { it.severity == Severity.ERROR }.joinToString { it.message })
        val snaps = su.filter { it.snapshot.stdlib }.associateBy { it.alias }
        val listExts = snaps["list"]?.snapshot?.exts?.get("List")?.keys ?: emptySet()
        listOf("map", "fold", "pure", "flatMap", "add").forEach {
            assertTrue(it in listExts, "List 扩展面含 $it")
        }
        val optExts = snaps["optional"]?.snapshot?.exts?.get("Optional")?.keys ?: emptySet()
        listOf("map", "fold", "pure", "flatMap").forEach {
            assertTrue(it in optExts, "Optional 扩展面含 $it")
        }
        val setExts = snaps["set"]?.snapshot?.exts?.get("Set")?.keys ?: emptySet()
        listOf("map", "fold").forEach { assertTrue(it in setExts, "Set 扩展面含 $it") }
    }


    // -------------------- 门 2：接收者形态语言面 --------------------

    /** 接收者形态特质声明编译绿——kind 形参 F 查名认 theory 参数，W-EXT-OBSOLETE 假阳消除。 */
    @Test fun 接收者形态声明无假阳() {
        val o = scriptStd(
            "trait Functor[F[_]] {\n    fun F[A].map[A, B](f: (A) => B): F[B]\n}\n" +
                "fun m(): Nat { unchecked return 1 }\n")
        assertEquals(emptyList<String>(), errs(o), "接收者形态特质声明绿: " + errs(o))
        assertFalse(o.diag.diags.any { it.code == "W-EXT-OBSOLETE" },
            "F 头为 kind 形参——不落 W-EXT-OBSOLETE 假阳")
        assertTrue(Regex(""""kind":\s*"HirTraitDecl"""").containsMatchIn(o.json), "特质入 HIR")
        assertTrue(Regex(""""receiver":\s*\{[^}]*"name":\s*"F"""").containsMatchIn(o.json),
            "map 成员的接收者位落 HIR（HirNamedType F）")
    }

    /** 对照：真拼错的接收者头仍落 W-EXT-OBSOLETE（假阳消除不动真阳）。 */
    @Test fun 真未知头仍警告() {
        val o = scriptStd("fun Natt.foo(): Nat = 1\nfun m(): Nat { unchecked return 1 }\n")
        assertTrue(o.diag.diags.any { it.code == "W-EXT-OBSOLETE" }, "真未知头 Natt 仍落 W-EXT-OBSOLETE")
    }

    // -------------------- 门 3：满足面双通道 --------------------

    /** 通道①：接收者形态规范 ⇄ 同形扩展函数（形状恒等）——界满足绿。 */
    @Test fun 接收者形态满足绿() {
        val o = scriptStd(
            "trait Functor[F[_]] {\n    fun F[A].map[A, B](f: (A) => B): F[B]\n}\n" +
                "fun List[a].map[a, b](f: (a) => b): List[b] = Nil\n" +
                "fun use[T: Functor](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var xs : List[Nat] = Nil; unchecked return use(xs) }\n")
        assertEquals(emptyList<String>(), errs(o), "T: Functor 经同形 List.map 实例满足: " + errs(o))
    }

    /** 通道②：无接收者规范（pure）⇐ 自由函数——界满足绿（今天只匹扩展函数的实缺口）。 */
    @Test fun pure自由函数通道绿() {
        val o = scriptStd(
            "trait Applicative[F[_]] {\n    fun pure[A](x: A): F[A]\n}\n" +
                "fun pure[A](x: A): Optional[A] = Some(x)\n" +
                "fun use[T: Applicative](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var o : Optional[Nat] = None; unchecked return use(o) }\n")
        assertEquals(emptyList<String>(), errs(o), "T: Applicative 经自由函数 pure 满足: " + errs(o))
    }

    /** 双通道混合：Applicative = pure（自由函数）+ ap（扩展函数）——同特质两通道并行。 */
    @Test fun 双通道混合满足() {
        val o = scriptStd(
            "trait Applicative[F[_]] {\n    fun pure[A](x: A): F[A]\n" +
                "    fun F[A].ap[A, B](ff: F[(A) => B]): F[B]\n}\n" +
                "fun pure[A](x: A): Optional[A] = Some(x)\n" +
                "fun Optional[a].ap[a, b](ff: Optional[(a) => b]): Optional[b] = None\n" +
                "fun use[T: Applicative](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var o : Optional[Nat] = None; unchecked return use(o) }\n")
        assertEquals(emptyList<String>(), errs(o), "pure 走自由函数 + ap 走扩展函数（双通道混合）: " + errs(o))
    }

    /** stdlib 特质**不经裸名链**（R-22）：界名裸写 `T: Functor` 无本地声明时落 E-UNBOUND-NAME
     *  ——stdlib 特质的引用通道是**限定父链**（traits.Functor），traitDecls 注入供满足判定用。 */
    @Test fun stdlib特质裸名界不解析() {
        val o = scriptStd(
            "fun use[T: Functor](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var xs : List[Nat] = Nil; unchecked return use(xs) }\n")
        assertTrue(errs(o).any { it == "E-UNBOUND-NAME" },
            "stdlib Functor 不经裸名链：界名裸写须本地声明（R-22 限定唯一通道）: " + errs(o))
    }

    /** stdlib 特质经**限定父链**注入且可用：M : traits.Functor——继承规范 map 由 stdlib 挂载 ext 链
     *  （t134 方法糖查询链同源）供给；负例（容器无实例）判缺失精确报。 */
    @Test fun stdlib特质经父链注入满足() {
        // 继承规范 map 无本地实例——由 stdlib List.map 挂载 ext 供给（auto-impl 声明即满足）
        val o = scriptStd(
            "trait M[F[_]] : traits.Functor[F] {\n    fun F[A].mmap[A, B](f: (A) => B): F[B]\n}\n" +
                "fun List[a].mmap[a, b](f: (a) => b): List[b] = Nil\n" +
                "fun use[T: M](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var xs : List[Nat] = Nil; unchecked return use(xs) }\n")
        assertEquals(emptyList<String>(), errs(o),
            "M : traits.Functor 由 stdlib List.map 挂载 ext 满足（无本地 map 实例）: " + errs(o))
        // 负例：Nat 无 map 扩展 ⇒ 缺失规范精确报
        val o2 = scriptStd(
            "trait M[F[_]] : traits.Functor[F] {\n    fun F[A].mmap[A, B](f: (A) => B): F[B]\n}\n" +
                "fun Nat.mmap[a, b](f: (a) => b): Nat = 1\n" +
                "fun use[T: M](x: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { unchecked return use(1) }\n")
        val miss = o2.diag.diags.filter { it.code == "E-TC-NOT-SATISFIED" }
        assertTrue(miss.isNotEmpty(), "Nat 无 map 实例 ⇒ 缺失规范精确报")
        assertTrue(miss.any { it.message.contains("map") }, "缺失条目含继承的 map 规范: " + miss.map { it.message })
    }

    // -------------------- 门 4：负例先红 --------------------

    /** 缺实例：缺失规范清单精确报（Trait 名 + 成员名）。 */
    @Test fun 负例缺失规范形态() {
        val o = scriptStd(
            "trait Functor[F[_]] {\n    fun F[A].map[A, B](f: (A) => B): F[B]\n}\n" +
                "fun use[T: Functor](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var xs : List[Nat] = Nil; unchecked return use(xs) }\n")
        val miss = o.diag.diags.filter { it.code == "E-TC-NOT-SATISFIED" }
        assertTrue(miss.isNotEmpty(), "缺实例先红")
        assertTrue(miss.any { it.message.contains("Functor.") && it.message.contains("map") },
            "缺失条目含规范名: " + miss.map { it.message })
    }

    /** 签名不符（形状不恒等）：map 带两参 → 缺失（同形扩展函数是唯一满足面）。 */
    @Test fun 负例签名不符() {
        val o = scriptStd(
            "trait Functor[F[_]] {\n    fun F[A].map[A, B](f: (A) => B): F[B]\n}\n" +
                "fun List[a].map[a, b](f: (a) => b, g: (a) => b): List[b] = Nil\n" +
                "fun use[T: Functor](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var xs : List[Nat] = Nil; unchecked return use(xs) }\n")
        assertTrue(errs(o).any { it == "E-TC-NOT-SATISFIED" }, "签名不符（实参数不符）判未满足")
    }

    /** 无接收者规范若无自由函数同名实例 → 缺失（pure 通道是自由函数面，扩展函数同名不算）。 */
    @Test fun 负例纯扩展不满足pure() {
        val o = scriptStd(
            "trait Applicative[F[_]] {\n    fun pure[A](x: A): F[A]\n}\n" +
                "fun List[a].pure[a](x: a): List[a] = Nil\n" +
                "fun use[T: Applicative](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var xs : List[Nat] = Nil; unchecked return use(xs) }\n")
        assertTrue(errs(o).any { it == "E-TC-NOT-SATISFIED" },
            "pure 规范的满足面是自由函数——扩展函数同名不满足（通道隔离）")
    }

    /** traverse 边界实测：M[_]: Applicative 单子约束 V1 不可为界（E-TC-BOUNDARY 落红）。 */
    @Test fun traverse单子约束边界红() {
        val o = scriptStd(
            "trait Traversable[F[_]] {\n" +
                "    fun F[A].traverse[A, M[_]: Applicative, B](f: (A) => M[B]): M[F[B]]\n}\n" +
                "fun m(): Nat { unchecked return 1 }\n")
        assertTrue(errs(o).any { it == "E-TC-BOUNDARY" },
            "M[_]: Applicative 在 V1 不可为界（TS-24 边界——Traversable 本体挂账，卷 §10.2）")
    }

    // -------------------- 门 5：旧形态冻结 + 限定引用 --------------------

    /** 旧形态（首位 Self 吸收 / 自由函数）通道不变——冻结回归。 */
    @Test fun 旧形态通道冻结() {
        val o = scriptStd(
            "trait Show[T] {\n    fun show(x: T): Str\n}\n" +
                "@impure fun show(x: Nat): Str = \"n\"\n" +
                "fun use[T: Show](x: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { unchecked return use(1) }\n")
        assertEquals(emptyList<String>(), errs(o), "旧形态规范经自由函数满足（回归）: " + errs(o))
        val o2 = scriptStd(
            "trait Show[T] {\n    fun show(self: Self): Str\n}\n" +
                "@impure fun Nat.show(): Str = \"n\"\n" +
                "fun use[T: Show](x: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { unchecked return use(1) }\n")
        assertEquals(emptyList<String>(), errs(o2), "Self 吸收通道（ADR-24）冻结回归: " + errs(o2))
    }

    /** 限定形态引用 stdlib 特质：traits.Functor 作父链——限定类型面解析（D-MODULE-REF + 符号）。 */
    @Test fun 限定特质引用绿() {
        val o = scriptStd(
            "trait M[F[_]] : traits.Functor[F] {\n    fun F[A].mmap[A, B](f: (A) => B): F[B]\n}\n" +
                "fun List[a].map[a, b](f: (a) => b): List[b] = Nil\n" +
                "fun List[a].mmap[a, b](f: (a) => b): List[b] = Nil\n" +
                "fun use[T: M](xs: T): Nat { unchecked return 1 }\n" +
                "fun go(): Nat { var xs : List[Nat] = Nil; unchecked return use(xs) }\n")
        assertEquals(emptyList<String>(), errs(o),
            "限定父链 traits.Functor 解析绿（mmap 本地实例 + 继承闭包 map）: " + errs(o))
        assertTrue(Regex("D-MODULE-REF").containsMatchIn(o.json), "限定引用落 D-MODULE-REF 旁注")
    }

    /** 未挂载的限定前缀：t134 设计口径——单文件期形态保留（不落诊断，HirQualifiedType 原样）。 */
    @Test fun 未挂载前缀形态保留() {
        val o = compileFrontendImpl(
            "trait M[F[_]] : nosuch.Functor[F] { }\nfun m(): Nat { unchecked return 1 }\n",
            "t140ns.subl", true, false, false, emptyMap(), sublimation.frontend.diag.DiagBag())
        assertEquals(emptyList<String>(), errs(o), "未挂载前缀不落诊断（t134 形态保留口径）: " + errs(o))
        assertTrue(Regex("HirQualifiedType").containsMatchIn(o.json),
            "限定类型形态保留入 HIR（HirQualifiedType）")
    }
}

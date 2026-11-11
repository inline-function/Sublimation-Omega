package sublimation.frontend.semantic

import sublimation.frontend.ast.AtomicType
import sublimation.frontend.ast.FunType
import sublimation.frontend.ast.MutType
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.QualifiedType
import sublimation.frontend.ast.TupleType
import sublimation.frontend.ast.UnionType
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirAtomicType
import sublimation.frontend.hir.HirFunType
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirQualifiedType
import sublimation.frontend.hir.HirTupleType
import sublimation.frontend.hir.HirUnionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * t38（ADR-26）：扩展函数桶键规范化的**登记/查找对偶一致性**守护测试。
 *
 * 登记侧（Collector：AST receiver）与查找侧（Elaborator：实参 HIR 静态类型）经同一对象
 * ExtFunHeads 的两个投影取键——本测试断言两侧对**同形类型**产出**完全相同的键**，
 * 杜绝「登记与查找两套口径」的静默漂移。键规范表逐行落实（卷 TS-11 / ADR-26）：
 */
class ExtFunHeadsTest {

    private val pos = SourceRange(SourcePos(1, 1), null)
    private fun nid(n: Int) = n   // NodeId = Int typealias（定义于 diag 包，hir 经导入使用）

    @Test
    fun namedHeadIsJustTheName() {
        assertEquals("Nat", ExtFunHeads.ofAst(NamedType("Nat", emptyList(), pos)))
        assertEquals("Nat", ExtFunHeads.ofHir(HirNamedType("Nat", emptyList(), null, nid(1), pos)))
    }

    @Test
    fun appliedHeadStaysTheNameArgsStayInSignature() {
        assertEquals("List", ExtFunHeads.ofAst(NamedType("List", listOf(NamedType("Nat", emptyList(), pos)), pos)))
        assertEquals("List", ExtFunHeads.ofHir(
            HirNamedType("List", listOf(HirNamedType("Nat", emptyList(), null, nid(2), pos)), null, nid(3), pos)))
    }

    @Test
    fun qualifiedHeadKeepsTheChain() {
        assertEquals("core.Point", ExtFunHeads.ofAst(QualifiedType(listOf("core"), "Point", emptyList(), pos)))
        assertEquals("core.Point", ExtFunHeads.ofHir(
            HirQualifiedType(listOf("core"), "Point", emptyList(), null, nid(4), pos)))
    }

    @Test
    fun tupleHeadIsShapeOverArity() {
        val two = listOf(NamedType("Nat", emptyList(), pos), NamedType("Nat", emptyList(), pos))
        assertEquals("Tuple/2", ExtFunHeads.ofAst(TupleType(two, pos)))
        assertEquals("Tuple/2", ExtFunHeads.ofHir(
            HirTupleType(two.map { HirNamedType("Nat", emptyList(), null, nid(5), pos) }, nid(6), pos)))
        val three = two + NamedType("Nat", emptyList(), pos)
        assertEquals("Tuple/3", ExtFunHeads.ofAst(TupleType(three, pos)))
        assertEquals("Tuple/3", ExtFunHeads.ofHir(
            HirTupleType(three.map { HirNamedType("Nat", emptyList(), null, nid(7), pos) }, nid(8), pos)))
    }

    @Test
    fun funHeadIsShapeOverArity() {
        val one = listOf(NamedType("Nat", emptyList(), pos))
        assertEquals("Fun/1", ExtFunHeads.ofAst(FunType(one, NamedType("Nat", emptyList(), pos), pos = pos)))
        assertEquals("Fun/1", ExtFunHeads.ofHir(
            HirFunType(one.map { HirNamedType("Nat", emptyList(), null, nid(9), pos) },
                HirNamedType("Nat", emptyList(), null, nid(10), pos), nid = nid(11), origin = pos)))
        val two = one + NamedType("Nat", emptyList(), pos)
        assertEquals("Fun/2", ExtFunHeads.ofAst(FunType(two, NamedType("Nat", emptyList(), pos), pos = pos)))
        assertEquals("Fun/2", ExtFunHeads.ofHir(
            HirFunType(two.map { HirNamedType("Nat", emptyList(), null, nid(12), pos) },
                HirNamedType("Nat", emptyList(), null, nid(13), pos), nid = nid(14), origin = pos)))
    }

    @Test
    fun unionHeadIsShapeOverVariantCount() {
        val vs = listOf(NamedType("Nat", emptyList(), pos), NamedType("Str", emptyList(), pos))
        assertEquals("Union/2", ExtFunHeads.ofAst(UnionType(vs, pos)))
        assertEquals("Union/2", ExtFunHeads.ofHir(
            HirUnionType(vs.map { HirNamedType(it.name, emptyList(), null, nid(15), pos) }, nid(16), pos)))
    }

    @Test
    fun nestedTupleInsideFunDoesNotAffectOuterKey() {
    // ((Nat, Nat) => Nat)：外层函数 1 参数（参数本身是元组）→ Fun/1；内层形状进签名比对
        val inner = TupleType(listOf(NamedType("Nat", emptyList(), pos), NamedType("Nat", emptyList(), pos)), pos)
        assertEquals("Fun/1", ExtFunHeads.ofAst(FunType(listOf(inner), NamedType("Nat", emptyList(), pos), pos = pos)))
        assertEquals("Fun/1", ExtFunHeads.ofHir(
            HirFunType(listOf(HirTupleType(listOf(
                HirNamedType("Nat", emptyList(), null, nid(17), pos),
                HirNamedType("Nat", emptyList(), null, nid(18), pos)), nid(19), pos)),
                HirNamedType("Nat", emptyList(), null, nid(20), pos), nid = nid(21), origin = pos)))
    }

    @Test
    fun mutAndAtomicDelegateToInnerHead() {
        assertEquals("Nat", ExtFunHeads.ofAst(MutType(NamedType("Nat", emptyList(), pos), pos)))
        assertEquals("Nat", ExtFunHeads.ofAst(AtomicType(NamedType("Nat", emptyList(), pos), pos)))
        assertEquals("Nat", ExtFunHeads.ofHir(HirMutType(HirNamedType("Nat", emptyList(), null, nid(22), pos), nid(23), pos)))
        assertEquals("Nat", ExtFunHeads.ofHir(HirAtomicType(HirNamedType("Nat", emptyList(), null, nid(24), pos), nid(25), pos)))
    }

    @Test
    fun syntheticAndErrorHeadsAreNotRegistrable() {
        assertEquals(null, ExtFunHeads.ofAst(sublimation.frontend.ast.ErrorType("破裂", pos)))
        assertEquals(null, ExtFunHeads.ofHir(HirNamedType("(推导:x)", emptyList(), null, nid(26), pos)))
        assertEquals(null, ExtFunHeads.ofHir(HirNamedType("(未密封)", emptyList(), null, nid(27), pos)))
        assertEquals(null, ExtFunHeads.ofHir(null))
    }
}

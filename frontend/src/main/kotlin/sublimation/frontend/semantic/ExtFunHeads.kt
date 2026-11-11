package sublimation.frontend.semantic

import sublimation.frontend.ast.AtomicType
import sublimation.frontend.ast.ErrorType
import sublimation.frontend.ast.FunType
import sublimation.frontend.ast.MutType
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.RefType
import sublimation.frontend.ast.QualifiedType
import sublimation.frontend.ast.TupleType
import sublimation.frontend.ast.Type
import sublimation.frontend.ast.UnionType
import sublimation.frontend.hir.HirAtomicType
import sublimation.frontend.hir.HirFunType
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirQualifiedType
import sublimation.frontend.hir.HirRefType
import sublimation.frontend.hir.HirSelfType
import sublimation.frontend.hir.HirTupleType
import sublimation.frontend.hir.HirType
import sublimation.frontend.hir.HirUnionType

/**
 * 扩展函数桶键的**唯一规范化源**（t38 / ADR-26 / 卷 TS-11）。
 *
 * 桶键 = (接收者类型头, 名字)。具名类型的头即名字；**无名字的形状类型**按「形状/元数」
 * 规范化——登记侧（Collector：AST receiver）与查找侧（Elaborator：实参的 HIR 静态类型）
 * **必须产出同一套键**，故两侧投影自本对象的同一张规范表（单一函数对两层类型表示的两个投影）：
 *
 * | 接收者              | 键                          |
 * |--------------------|------------------------------|
 * | Nat                | Nat                          |
 * | List[A]            | List（类型实参留在签名比对） |
 * | core.Point         | core.Point（限定链保留）     |
 * | (Nat, Nat)         | Tuple/2                      |
 * | (Nat, Nat, Nat)    | Tuple/3                      |
 * | (Nat) => Nat       | Fun/1                        |
 * | (Nat, Nat) => Nat  | Fun/2                        |
 * | A \| B             | Union/2                      |
 * | ((Nat,Nat) => Nat) | Fun/1（嵌套形状进签名比对）  |
 * | @mut T / @atomic T | 同内层头                     |
 *
 * null = 不可规范化（ErrorType / 合成体 / 未密封 / Self）——不登记、不查找。
 */
object ExtFunHeads {

    private fun composite(kind: String, arity: Int): String = "$kind/$arity"

    /** 登记侧投影：AST 接收者类型 → 桶键。 */
    fun ofAst(t: Type): String? = when (t) {
        is NamedType -> t.name
        is QualifiedType -> (t.module + t.name).joinToString(".")
        is TupleType -> composite("Tuple", t.items.size)
        is FunType -> composite("Fun", t.params.size)
        is UnionType -> composite("Union", t.variants.size)
        is MutType -> ofAst(t.inner)
        is AtomicType -> ofAst(t.inner)
        is RefType -> ofAst(t.inner)   // t95：@ref 剥落（与 @mut 同构）
        is ErrorType -> null
    }

    /** 查找侧投影：HIR 静态类型 → 桶键（与 [ofAst] 同表同构——两侧一致性由 ExtFunHeadsTest 对偶断言守护）。 */
    fun ofHir(t: HirType?): String? = when (t) {
        is HirNamedType ->
            if (t.name.startsWith("(推导:") || t.name == "(未密封)") null else t.name
        is HirQualifiedType -> (t.module + t.name).joinToString(".")
        is HirTupleType -> composite("Tuple", t.items.size)
        is HirFunType -> composite("Fun", t.params.size)
        is HirUnionType -> composite("Union", t.variants.size)
        is HirMutType -> ofHir(t.inner)
        is HirAtomicType -> ofHir(t.inner)
        is HirRefType -> ofHir(t.inner)   // t95
        is HirSelfType -> null
        else -> null
    }
}

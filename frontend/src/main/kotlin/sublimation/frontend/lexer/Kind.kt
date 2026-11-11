package sublimation.frontend.lexer

/**
 * 词法单元类别，共 71 项——《形式化文法》§1.7「Token 全表」。
 *
 * 契约：Kind 只描述 token 的类别，不承载任何语法/语义判定；邻接消歧（D 层）读的是
 * [Token.precededBySpace] 而非 Kind。表内保留若干无产生式消费的条目，均为有意保留：
 * IN 为休眠项（lexer 从不产出，L-9）；BACKSLASH 已退役、PLUS_BECAUSE 无消费点——两者 lexer 照常产出，
 * 仅为下游诊断定位（同上 §1.7 备注）；ARROW 仅在 when 分支箭头位合法（t42 裁决，其余位置非法）。
 */
enum class Kind {
    INT, FLOAT, STR, IDENT, SYMBOL,

    // 硬关键字（L-9）：16 个，由 IDENT 读出后查表折算
    FUN, TRAIT, STRUCT, ENUM, VAR, IMPL, FOR, IF, ELSE, UNCHECKED, WHEN, IS, BY, TYPE, RET, IN, DEF,

    // 括号
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,

    // 标点
    COMMA, SEMI, COLON, COLON2, DOT, AT, BACKSLASH, DOLLAR, HASH,

    // 算术（无优先级，D-10）
    PLUS, MINUS, STAR, SLASH, PERCENT, POW,

    // 比较/赋值
    ASSIGN, EQ, NEQ, LE, GE,

    // 区间
    RANGE,

    // 箭头：DARROW 程序位（lambda/函数类型）、ARROW 仅 when 分支箭头位合法（t42 裁决 2026-11-06；其余位置非法）、IFF 与 EQUIV 并存（§3.1）
    ARROW, DARROW, IFF,

    // 位/单目逻辑（&& 与 || 在词法层已归一为 Unicode 形，见 Lexer）
    AMP, PIPE, BANG,

    // Unicode 逻辑（L-8【逻辑单例】：固定单 token，不可作符号名）
    FORALL, EXISTS, EXISTS1, LAND, LOR, LNOT, IMPLIES, EQUIV, TOP, BOT,

    // 证明
    BECAUSE, THEREFORE, PLUS_BECAUSE,

    // 原子比较（邻接消歧见 D-1/D-2）
    LT, GT,

    // 结束（恒带 precededBySpace=⊤）
    EOF,
}

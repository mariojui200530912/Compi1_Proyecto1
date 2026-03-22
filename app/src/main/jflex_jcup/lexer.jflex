package com.compi1.proyecto1.analizadores;

import java_cup.runtime.*;
import com.compi1.proyecto1.interprete.ManejadorErrores;

%%

%class Lexer
%public
%unicode
%line
%column
%char
%cup

%state ESTADO_CADENA
%{
    StringBuilder cadenaConstruida = new StringBuilder();
    public boolean modoEditor = false;
    public int getYyChar() {
        return (int) yychar;
    }

    private Symbol symbol(int type) {
        return new Symbol(type, yyline + 1, yycolumn + 1);
    }

    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline + 1, yycolumn + 1, value);
    }
%}

/* ==========================================================================
   MACROS (Expresiones Regulares)
   ========================================================================== */

// 1. Espacios y Comentarios
SaltoLinea      = \r|\n|\r\n
EspacioBlanco   = {SaltoLinea} | [ \t\f]
ComentarioLinea = "$" [^\r\n]* {SaltoLinea}?
ComentarioMulti = "/*" [^*]* ("*" [^/][^*]*)* "*/"

// 2. Componentes básicos
Numero         = [:digit:]+ (\. [:digit:]+)?
// Un identificador empieza con letra o guion bajo, seguido de letras, números o guiones bajos
Identificador  = [:jletter:] [:jletterdigit:]*

// 3. Colores
ColorHex       = "#" ([:digit:] | [a-fA-F]){6}

%%

/* ==========================================================================
   REGLAS LÉXICAS
   ========================================================================== */
<YYINITIAL> {

    /* Ignorar espacios y comentarios */
    {EspacioBlanco}     { /* Ignorar */ }
    {ComentarioLinea}   { /* Ignorar */ }
    {ComentarioMulti}   { /* Ignorar */ }

    /* --- Componentes Principales (MAYÚSCULAS) --- */
    "SECTION"           { return symbol(sym.SECTION); }
    "TABLE"             { return symbol(sym.TABLE); }
    "TEXT"              { return symbol(sym.TEXT); }
    "OPEN_QUESTION"     { return symbol(sym.OPEN_QUESTION); }
    "DROP_QUESTION"     { return symbol(sym.DROP_QUESTION); }
    "SELECT_QUESTION"   { return symbol(sym.SELECT_QUESTION); }
    "MULTIPLE_QUESTION" { return symbol(sym.MULTIPLE_QUESTION); }

    /* --- Atributos de los componentes (minúsculas según enunciado) --- */
    "width"             { return symbol(sym.WIDTH); }
    "height"            { return symbol(sym.HEIGHT); }
    "pointX"            { return symbol(sym.POINTX); }
    "pointY"            { return symbol(sym.POINTY); }
    "orientation"       { return symbol(sym.ORIENTATION); }
    "elements"          { return symbol(sym.ELEMENTS); }
    "styles"            { return symbol(sym.STYLES); }
    "content"           { return symbol(sym.CONTENT); }
    "label"             { return symbol(sym.LABEL); }
    "options"           { return symbol(sym.OPTIONS); }
    "correct"           { return symbol(sym.CORRECT); }
    "who_is_that_pokemon" { return symbol(sym.WHO_IS_THAT_POKEMON); }

    /* --- Valores Constantes / Enums (MAYÚSCULAS) --- */
    "VERTICAL"          { return symbol(sym.VERTICAL); }
    "HORIZONTAL"        { return symbol(sym.HORIZONTAL); }
    "MONO"              { return symbol(sym.MONO); }
    "SANS_SERIF"        { return symbol(sym.SANS_SERIF); }
    "CURSIVE"           { return symbol(sym.CURSIVE); }
    "LINE"              { return symbol(sym.LINE); }
    "DOTTED"            { return symbol(sym.DOTTED); }
    "DOUBLE"            { return symbol(sym.DOUBLE); }


    /* --- Tipos de Datos y Variables --- */
    "number"            { return symbol(sym.TIPO_NUMBER); }
    "string"            { return symbol(sym.TIPO_STRING); }
    "special"           { return symbol(sym.TIPO_SPECIAL); }
    "draw"              { return symbol(sym.DRAW); }

    /* --- Estructuras de Control --- */
    "IF"                { return symbol(sym.IF); }
    "ELSE"              { return symbol(sym.ELSE); }
    "WHILE"             { return symbol(sym.WHILE); }
    "DO"                { return symbol(sym.DO); }
    "FOR"               { return symbol(sym.FOR); }
    "in"                { return symbol(sym.IN); }

    /* --- Colores Predefinidos --- */
    "RED"               { return symbol(sym.COLOR_RED); }
    "BLUE"              { return symbol(sym.COLOR_BLUE); }
    "GREEN"             { return symbol(sym.COLOR_GREEN); }
    "PURPLE"            { return symbol(sym.COLOR_PURPLE); }
    "SKY"               { return symbol(sym.COLOR_SKY); }
    "YELLOW"            { return symbol(sym.COLOR_YELLOW); }
    "BLACK"             { return symbol(sym.COLOR_BLACK); }
    "WHITE"             { return symbol(sym.COLOR_WHITE); }

    /* --- Operadores Aritméticos --- */
    "+"                 { return symbol(sym.MAS); }
    "-"                 { return symbol(sym.MENOS); }
    "*"                 { return symbol(sym.POR); }
    "/"                 { return symbol(sym.DIVIDIDO); }
    "^"                 { return symbol(sym.POTENCIA); }
    "%"                 { return symbol(sym.MODULO); }

    /* --- Operadores de Comparación --- */
    ">"                 { return symbol(sym.MAYOR); }
    ">="                { return symbol(sym.MAYOR_IGUAL); }
    "<"                 { return symbol(sym.MENOR); }
    "<="                { return symbol(sym.MENOR_IGUAL); }
    "=="                { return symbol(sym.IGUALDAD); }
    "!!"                { return symbol(sym.DIFERENTE); }

    /* --- Operadores Lógicos --- */
    "||"                { return symbol(sym.OR); }
    "&&"                { return symbol(sym.AND); }
    "~"                 { return symbol(sym.NOT); }

    /* --- Símbolos de Agrupación y Puntuación --- */
    "("                 { return symbol(sym.PAR_IZQ); }
    ")"                 { return symbol(sym.PAR_DER); }
    "["                 { return symbol(sym.CORCHETE_IZQ); }
    "]"                 { return symbol(sym.CORCHETE_DER); }
    "{"                 { return symbol(sym.LLAVE_IZQ); }
    "}"                 { return symbol(sym.LLAVE_DER); }
    ","                 { return symbol(sym.COMA); }
    ";"                 { return symbol(sym.PUNTO_COMA); }
    ":"                 { return symbol(sym.DOS_PUNTOS); }
    "="                 { return symbol(sym.ASIGNACION); }
    "."                 { return symbol(sym.PUNTO); }
    ".."                { return symbol(sym.RANGO); }
    "?"                 { return symbol(sym.COMODIN); }
    "\""                {
                                if (modoEditor) {
                                    yybegin(ESTADO_CADENA);
                                    return symbol(996, yytext()); // 996: Token especial para COMILLAS
                                } else {
                                    cadenaConstruida.setLength(0);
                                    yybegin(ESTADO_CADENA);
                                }
                            }

    /* --- Reconocimiento de Literales y Macros --- */
    {ColorHex}          { return symbol(sym.COLOR_HEX, yytext()); }
    {Numero}            { return symbol(sym.NUMERO, yytext()); }
    {Identificador}     { return symbol(sym.IDENTIFICADOR, yytext()); }

}

<ESTADO_CADENA> {

    \" {
        yybegin(YYINITIAL);
        if (modoEditor) return symbol(996, yytext()); // 996: COMILLAS
        else return symbol(sym.CADENA, cadenaConstruida.toString());
    }

    // --- Traducción y Separación de Emojis ---
    // Si es editor, devuelve el token 997 (Emoji literal). Si es CUP, lo traduce y lo guarda.
    "@[:" ")"+ "]" | "@[:smile:]"      { if(modoEditor) return symbol(997, yytext()); else cadenaConstruida.append("😀"); }
    "@[:" "("+ "]" | "@[:sad:]"        { if(modoEditor) return symbol(997, yytext()); else cadenaConstruida.append("😢"); }
    "@[:" "|"+ "]" | "@[:serious:]"    { if(modoEditor) return symbol(997, yytext()); else cadenaConstruida.append("😐"); }
    "@[<" "3"+ "]" | "@[:heart:]"      { if(modoEditor) return symbol(997, yytext()); else cadenaConstruida.append("❤️"); }
    "@[:star:]"                        { if(modoEditor) return symbol(997, yytext()); else cadenaConstruida.append("⭐"); }
    "@[:^^:]" | "@[:cat:]"             { if(modoEditor) return symbol(997, yytext()); else cadenaConstruida.append("🐱"); }

    // Manejo especial para :star-number: (Ej: @[:star-5:] o @[:star:5:])
    "@[:star" [:\-] [0-9]+ ":]" {
        if (modoEditor) {
            return symbol(997, yytext());
        } else {
            String txt = yytext();
            String numStr = txt.replaceAll("[^0-9]", "");
            int n = Integer.parseInt(numStr);
            for(int i = 0; i < n; i++) {
                cadenaConstruida.append("⭐");
            }
        }
    }

    // --- Captura de Texto Normal dentro de la cadena ---
    [^\n\r\"@]+ {
        if (modoEditor) return symbol(998, yytext());
        else cadenaConstruida.append(yytext());
    }

    "@" {
        if (modoEditor) return symbol(998, yytext());
        else cadenaConstruida.append("@");
    }
}

/* --- Manejo de Errores Léxicos --- */
[^] {
          ManejadorErrores.agregarError(yytext(), yyline + 1, yycolumn + 1, "Léxico", "Símbolo no reconocido");
}
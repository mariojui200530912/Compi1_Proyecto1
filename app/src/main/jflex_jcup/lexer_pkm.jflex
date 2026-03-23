package com.compi1.proyecto1.analizadores;

import java_cup.runtime.*;
import com.compi1.proyecto1.interprete.ManejadorErrores; // Reutilizamos tu caja de errores

%%

%class LexerPKM
%public
%unicode
%line
%column
%char
%cup
%ignorecase

// --- MACROS ---
LineTerminator = \r|\n|\r\n
WhiteSpace     = {LineTerminator} | [ \t\f]

// Numeros (enteros o decimales, positivos o negativos)
Numero = -?[0-9]+(\.[0-9]+)?

// Cadenas de texto entre comillas
Cadena = \"[^\"]*\"

// Colores en formato Hexadecimal (#FFFFFF)
ColorHex = #[0-9a-fA-F]+

// Colores RGB (<10,10,10>).
ColorRGB = "<"[ ]*[0-9]+[ ]*","[ ]*[0-9]+[ ]*","[ ]*[0-9]+[ ]*">"

%{

    private Symbol symbol(int type) {
        return new Symbol(type, yyline + 1, yycolumn + 1);
    }

    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline + 1, yycolumn + 1, value);
    }
%}

%%

<YYINITIAL> {
    /* --- IGNORAR ESPACIOS BLANCOS --- */
    {WhiteSpace} { /* Ignorar */ }

    /* --- SIMBOLOS DE ESTRUCTURA --- */
    "###" { return symbol(sym_pkm.HASH_TRES, yytext()); }
    "<"   { return symbol(sym_pkm.MENOR_QUE, yytext()); }
    ">"   { return symbol(sym_pkm.MAYOR_QUE, yytext()); }
    "/"   { return symbol(sym_pkm.BARRA, yytext()); }
    "="   { return symbol(sym_pkm.IGUAL, yytext()); }
    ","   { return symbol(sym_pkm.COMA, yytext()); }
    "{"   { return symbol(sym_pkm.LLAVE_IZQ, yytext()); }
    "}"   { return symbol(sym_pkm.LLAVE_DER, yytext()); }

    /* --- PALABRAS RESERVADAS (ETIQUETAS) --- */
    "style"            { return symbol(sym_pkm.STYLE, yytext()); }
    "section"          { return symbol(sym_pkm.SECTION, yytext()); }
    "content"          { return symbol(sym_pkm.CONTENT, yytext()); }
    "table"            { return symbol(sym_pkm.TABLE, yytext()); }
    "line"             { return symbol(sym_pkm.LINE, yytext()); }
    "element"          { return symbol(sym_pkm.ELEMENT, yytext()); }
    "text"             { return symbol(sym_pkm.TEXT, yytext()); }
    "open"             { return symbol(sym_pkm.OPEN, yytext()); }
    "drop"             { return symbol(sym_pkm.DROP, yytext()); }
    "select"           { return symbol(sym_pkm.SELECT, yytext()); }
    "multiple"         { return symbol(sym_pkm.MULTIPLE, yytext()); }

    /* --- ATRIBUTOS DE ESTILO --- */
    "color"            { return symbol(sym_pkm.COLOR, yytext()); }
    "background color" { return symbol(sym_pkm.BG_COLOR, yytext()); }
    "font family"      { return symbol(sym_pkm.FONT_FAMILY, yytext()); }
    "text size"        { return symbol(sym_pkm.TEXT_SIZE, yytext()); }
    "border"           { return symbol(sym_pkm.BORDER, yytext()); }

    /* --- VALORES ENUMERADOS (CONSTANTES) --- */
    "VERTICAL"         { return symbol(sym_pkm.VERTICAL, yytext()); }
    "HORIZONTAL"       { return symbol(sym_pkm.HORIZONTAL, yytext()); }
    "MONO"             { return symbol(sym_pkm.MONO, yytext()); }
    "SANS_SERIF"       { return symbol(sym_pkm.SANS_SERIF, yytext()); }
    "CURSIVE"          { return symbol(sym_pkm.CURSIVE, yytext()); }
    "DOTTED"           { return symbol(sym_pkm.DOTTED, yytext()); }
    "DOUBLE"           { return symbol(sym_pkm.DOUBLE_TYPE, yytext()); }

    // Colores por nombre
    "BLACK"|"WHITE"|"RED"|"GREEN"|"BLUE"|"YELLOW"|"PURPLE"|"SKY"|"CYAN"|"MAGENTA"|"GRAY"
                           { return symbol(sym_pkm.COLOR_NAME, yytext()); }

    /* --- METADATOS (Palabras que aparecen entre los ###) --- */
    "Author:"|"Fecha:"|"Hora:"|"Description:"|"Total de Secciones:"|"Total de Preguntas:"|"Abiertas:"|"Desplegables:"|"Selección:"|"Múltiples:"
                       { return symbol(sym_pkm.META_KEY, yytext()); }

    /* --- TIPOS DE DATOS --- */
    {Numero}   { return symbol(sym_pkm.NUMERO, yytext()); }
    {Cadena}   {

        String cadenaLimpia = yytext().substring(1, yytext().length() - 1);
        return symbol(sym_pkm.CADENA, cadenaLimpia);
    }
    {ColorHex} { return symbol(sym_pkm.HEX_COLOR, yytext()); }
    {ColorRGB} { return symbol(sym_pkm.RGB_COLOR, yytext()); }

    /* --- IDENTIFICADORES Y TEXTO SUELTO  --- */
    [a-zA-Z_0-9\.:@]+ ("/" [a-zA-Z_0-9\.:@]+)* { return symbol(sym_pkm.TEXTO_LIBRE, yytext()); }
}

/* --- MANEJO DE ERRORES LEXICOS --- */
[^] {
    ManejadorErrores.agregarError(yytext(), yyline + 1, yycolumn + 1, "Léxico PKM", "Símbolo no reconocido en el archivo .pkm");
    return symbol(sym_pkm.error, yytext());
}
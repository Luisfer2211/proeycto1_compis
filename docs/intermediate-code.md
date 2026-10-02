# Código intermedio de Compiscript

El compilador traduce un programa Compiscript, ya validado léxica, sintáctica y semánticamente, a código de tres direcciones (TAC). No ejecuta ese código ni genera ensamblador. Si el análisis reporta cualquier error, la pestaña **Intermediate Code** queda vacía y no se emite ninguna instrucción.

## Forma de las instrucciones

Cada instrucción ocupa una línea. Los temporales se llaman `t0`, `t1`, … Las etiquetas de control empiezan con `L`. Las funciones globales usan `fn_nombre`. Los métodos usan `Clase_metodo`.

| Instrucción | Significado |
|-------------|-------------|
| `alloc x global 0` | Reserva la variable o constante `x` en el área global, offset 0 |
| `alloc x local 2` | Reserva `x` en el marco de la función actual, offset 2 |
| `x = y` | Copia |
| `t0 = a + b` | Operación aritmética o relacional (`+ - * / % < <= > >= == !=`) |
| `t0 = - a` | Menos unario |
| `t0 = not a` | Negación lógica. `true` es `1` y `false` es `0` |
| `t0 = cast.i2f a` | Promueve un integer a float antes de operar con un float |
| `t0 = a concat b` | Concatenación de strings con `+` |
| `t0 = newarray n` | Arreglo de `n` elementos. La longitud se consulta con `length` |
| `t0 = a[i]` / `a[i] = t0` | Lectura y escritura de arreglo |
| `ifFalse t0 goto L1` | Salto si el operando es falso (`0`) |
| `goto L1` / `L1:` | Salto incondicional y etiqueta |
| `fn_add:` / `enter fn_add 2` | Entrada a una función y tamaño de su registro de activación |
| `param x` / `t0 = call fn_add, 2` | Paso de parámetros y llamada. El número es la cantidad de `param` |
| `return` / `return t0` | Retorno |
| `t0 = new Perro` | Instancia. Los campos viven a offsets fijos |
| `field Animal.nombre 0` | Layout del campo |
| `t0 = obj.nombre` / `obj.nombre = t0` | Lectura y escritura de campo |
| `begin_try Lcatch` / `end_try` | Región protegida |
| `err = caught` | El identificador del `catch` recibe el valor capturado |
| `param static_link` | Enlace al marco de la función envolvente (closure) |

## Registros de activación

- Las variables de nivel programa son `GLOBAL`. Su offset crece en el área global.
- Una variable declarada dentro de una función es `LOCAL`. Su offset sale del marco de esa función (`fp + offset`), no del área global.
- Los parámetros son `PARAMETER` dentro del mismo marco. En un método, `this` ocupa el offset 0 y los parámetros declarados siguen después.
- Una función anidada marca `needsStaticLink`. Su llamada emite `param static_link` antes de los argumentos, así que el conteo del `call` suma uno.
- Cada `call` representa un marco nuevo. La recursión no se expande: el cuerpo de `factorial` contiene `call fn_factorial`.

La columna **Storage** de la tabla de símbolos muestra `GLOBAL`, `LOCAL`, `PARAMETER`, `FIELD` o `FRAME`. En una función, **Address** es el tamaño del marco. En un campo, es el offset dentro del objeto.

## Expresiones

`(a + b) * (c + d)` produce como máximo dos temporales vivos. El generador libera los temporales que ya usó como operandos y después pide el temporal del resultado, así que `t0` se recicla:

```
t0 = a + b
t1 = c + d
t0 = t0 * t1
```

`&&` y `||` no son operadores aritméticos. Se bajan a saltos `ifFalse` para no evaluar el lado derecho cuando el izquierdo ya decide el resultado. `!` emite `not`.

Si un operando es `integer` y el otro `float`, el integer se convierte con `cast.i2f` y la operación queda en float. Un `float` no se asigna a un `integer`; eso ya lo rechaza el análisis semántico y, por tanto, no hay TAC.

## Control, funciones, clases y excepciones

- `if/else`, `while`, `do-while` y `for` usan una etiqueta de condición, una de salida y, en el `for`, una de actualización. `break` salta a la salida del bucle activo y `continue` a la actualización o a la condición.
- `foreach` recorre `0 .. length-1` y asigna el elemento a la variable del iterador.
- `switch` compara el selector con cada `case` y salta al final del `switch` después del cuerpo de ese caso. `default` queda al final.
- Las funciones emiten su etiqueta, `enter`, el cuerpo y `return`. Una función anidada se rodea con `goto` para que el flujo del padre no caiga dentro de ella.
- `new Clase` reserva el objeto, pasa ese objeto como `this` y llama al constructor resuelto en la jerarquía. Si la clase hija no declara constructor, se llama al del padre (`Animal_constructor`).
- Los campos del hijo se colocan después de los del padre. `Perro` no mueve `Animal.nombre` del offset 0.
- Una llamada `obj.metodo()` resuelve el método en la clase estática del objeto. Un override emite `call Perro_hablar`; un método heredado emite la etiqueta de la clase que lo declaró.
- `try/catch` no lanza excepciones en runtime. Marca la región con `begin_try` / `end_try` y el handler recibe el valor en `nombre = caught`.

## Supuestos

- El TAC es una representación intermedia, no un programa ejecutable.
- Booleanos y condiciones usan `0` y `1`.
- Los arreglos guardan su longitud; `length a` la lee. El índice ya fue validado como `integer`.
- La resolución de métodos es estática, con la jerarquía de la tabla de símbolos.
- No hay código objeto ni intérprete en este proyecto.

# Generación de código MIPS

El compilador traduce el código de tres direcciones a MIPS para ejecutarlo en [MARS 4.5](https://dpetersanderson.github.io/MARS/). No vuelve a recorrer el árbol: si hay cualquier error léxico, sintáctico o semántico, no hay TAC y tampoco hay ensamblador.

## Cómo correrlo

```bash
mvn clean package
java -jar target/compiscript-analyzer-1.0-SNAPSHOT.jar
```

En la IDE: **Open .cps**, **Analyze**. La pestaña **Assembly** muestra el MIPS, **Activation** el árbol de marcos y **Registers** las decisiones de `getReg`. **Save .asm** guarda el texto. La IDE no ejecuta el ensamblador.

En MARS:

```bash
java -jar Mars4_5.jar nc programa.asm
```

El jar se descarga desde la página de MARS. No forma parte del repositorio.

| Programa | Salida |
|---|---|
| `ejemplos/mips/arithmetic_print.cps` | `7` |
| `ejemplos/mips/factorial_print.cps` | `120` |
| `ejemplos/mips/array_print.cps` | `1` y `9` |
| `ejemplos/mips/animal_print.cps` | `Toby makes noise` |
| `ejemplos/mips/perro_print.cps` | `Toby barks` |
| `ejemplos/mips/try_bounds.cps` | `index out of bounds` |
| `ejemplos/mips/closure_print.cps` | `15` |
| `ejemplos/mips/float_print.cps` | `3.5` |
| `ejemplos/mips/peephole.cps` | `8` |
| `ejemplos/mips/logical_print.cps` | `0` y `1` |

`print` agrega un salto de línea. Los booleanos se imprimen como `0` o `1`.

## Marco de activación

La palabra mide 4 bytes. El slot `address` de la tabla de símbolos está en `address * 4($fp)`. Después de los slots se guardan `$ra` y el `$fp` del llamador. Ese `$fp` guardado es el enlace al padre: en ejecución, la pila de marcos es el árbol de activación.

Las variables no viven en `$s`. Un global es la etiqueta `g_nombre`. Un local o parámetro se lee y escribe contra `$fp`. Bajo `$fp` hay 16 palabras para volcado de temporales y 16 para valores float.

El llamador empila los argumentos del último al primero, hace `jal` y después los desempila. El llamado los copia a sus slots. El resultado vuelve en `$v0`, o en `$f0` si es float. Antes de `jal` se vuelcan los temporales vivos, porque `$t` es caller-saved.

Una función anidada recibe `param static_link`: el `$fp` del padre. Si un nombre no está en el marco actual, se sigue ese enlace.

## getReg

`RegisterAllocator.getReg` hace tres cosas, en este orden:

1. Reutiliza el `$t` que ya contiene el temporal.
2. Si no, toma el `$t` libre de menor índice (`$t0`–`$t9`).
3. Si no hay libres, vuelca con `sw` el temporal cuyo próximo uso está más lejos y reutiliza su registro.

La pestaña **Registers** lista cada decisión. Con diez registros los ejemplos no desbordan, porque el TAC ya recicla temporales. La prueba con cupo de dos registros fuerza el volcado.

## Mirilla

Después de emitir las instrucciones se aplican seis transformaciones locales:

1. Se elimina `move $r, $r` y `addu $r, $r, $zero`.
2. Se elimina un `j` cuya etiqueta es la instrucción siguiente.
3. Se borra el código inalcanzable después de `j` o `jr`, hasta la próxima etiqueta.
4. Multiplicar o dividir por una potencia de dos pasa a `sll` o `sra`.
5. Sumar cero o multiplicar por uno pasa a `move`.
6. Se elimina el `lw` que repite el `sw` inmediato anterior, con el mismo registro y la misma dirección.

## Memoria y excepciones

- Un arreglo de `n` elementos ocupa `n + 1` palabras. La palabra 0 es la longitud. Cada acceso comprueba el índice.
- `new Clase` reserva los campos con syscall 9, incluidos los heredados. El método que se llama ya viene resuelto en el TAC: `Perro_hablar` o `Animal_constructor`. No hay vtable.
- `begin_try` empila la dirección del handler en `__hstack`. Si el índice es inválido y hay handler, `__caught` recibe el string `index out of bounds` y se salta al `catch`. Si no hay handler, se imprime ese mensaje y el programa termina.
- Las cadenas literales están en `.data`. La concatenación usa la rutina `__concat`.

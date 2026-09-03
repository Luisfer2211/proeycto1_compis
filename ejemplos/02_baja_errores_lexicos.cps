// Complejidad baja - solo errores lexicos (al menos 3)
let edad: integer = 20;
let nombre: string = "Ana";
let activo: boolean = true;
const LIMITE: integer = 100;

@
let suma: integer = edad + 5;
let doble: integer = edad * 2;

if (edad >= 18) {
  print("Mayor de edad");
} else {
  print("Menor de edad");
}

$
let contador: integer = 0;
while (contador < 3) {
  print(contador);
  contador = contador + 1;
}

`
switch (edad) {
  case 20:
    print("Tiene 20 anios");
  default:
    print("Otra edad");
}

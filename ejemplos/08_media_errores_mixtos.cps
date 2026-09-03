// Complejidad media - errores lexicos y sintacticos (al menos 2 de cada tipo)
class Persona {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function saludar(): string {
    return "Hola, soy " + this.nombre;
  }
}

class Estudiante : Persona {
  function saludar(): string {
    return "Estudiante " + this.nombre;
  }
}

function sumar(a: integer, b: integer): integer {
  return a + b;
}

function esPar(n: integer): boolean {
  return n % 2 == 0;
}

let edad: integer = 20;
let nombre: string = "Ana";
let activo: boolean = true;
const LIMITE: integer = 100;

@
let suma: integer = edad + 5;
let doble: integer = edad * 2;

if (edad >= 18 {
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

switch (edad) {
  case 20
    print("Tiene 20 anios");
  default:
    print("Otra edad");
}

let numeros: integer[] = [1, 2, 3];
print(numeros[0]);

let p1: Persona = new Persona("Ana");
let p2: Estudiante = new Estudiante("Luis");
print(p1.saludar());
print(p2.saludar());

print(sumar(1, 2));
print(esPar(4));

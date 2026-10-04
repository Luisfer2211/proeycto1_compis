class Animal {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " makes noise";
  }
}

class Perro : Animal {
  function hablar(): string {
    return this.nombre + " barks";
  }
}

let dog: Perro = new Perro("Toby");
print(dog.hablar());

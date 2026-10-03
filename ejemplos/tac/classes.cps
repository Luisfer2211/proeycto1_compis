class Animal {
  let nombre: string;

  function constructor(nombre: string) {
    this.nombre = nombre;
  }

  function hablar(): string {
    return this.nombre + " makes noise";
  }
}

let pet: Animal = new Animal("Toby");
print(pet.hablar());

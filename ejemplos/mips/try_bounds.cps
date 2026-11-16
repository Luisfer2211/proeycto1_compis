let lista: integer[] = [1, 2, 3];
try {
  let peligro: integer = lista[100];
  print(peligro);
} catch (err) {
  print(err);
}

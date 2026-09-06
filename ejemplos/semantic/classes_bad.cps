class Box {
  let value: integer;
}

let item: Box = new Box();
print(item.missing);
print(this.value);

class Child : MissingParent {
}

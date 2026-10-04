let x: integer = 0;
if (x < 1) {
  x = 2;
} else {
  x = 3;
}
while (x > 0) {
  x = x - 1;
}
do {
  x = x + 1;
} while (x < 1);
for (let i: integer = 0; i < 2; i = i + 1) {
  x = i;
}
let items: integer[] = [4, 5];
foreach (item in items) {
  x = item;
}
switch (x) {
  case 1:
    x = 10;
  default:
    x = 11;
}

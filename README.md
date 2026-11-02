# Invento

Proiect pentru gestiunea stocurilor din mai multe magazine, realizat in Java 21 cu Spring Boot si Maven.

Distribuitorul anunta o livrare, gestionarul o accepta sau o respinge, iar casierul inregistreaza vanzarile. Stocul este separat pe magazine si creste doar dupa acceptarea livrarii.

Momentan datele sunt tinute in memorie. Sunt implementate entitatile, validarile si serviciile, impreuna cu teste JUnit pentru calcule, livrari, vanzari si stocuri.

## Rulare

Ai nevoie de JDK 21 si Maven.

Pentru teste:

```shell
mvn test
```

Pentru pornirea aplicatiei:

```shell
mvn spring-boot:run
```

Aplicatia porneste momentan doar contextul Spring. Fluxurile se verifica prin teste.

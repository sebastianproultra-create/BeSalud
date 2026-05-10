# Quick Start

## Arrancar
```bash
mvn spring-boot:run
```
App en http://localhost:8080

## Tests
```bash
mvn test                        # todos
mvn test -pl . -Dtest=LoginControllerTest   # uno específico
```

## MongoDB
```bash
mongosh
use besalud
db.admins.find()
db.doctors.find()
db.pacientes.find()
db.citas.find()
db.citas.find({ estado: "PENDIENTE" })
db.getCollectionNames()
```

## Limpiar y recompilar
```bash
mvn clean install -DskipTests
```

# VertxPostgreSQLClient
Vert.x Application - Asynchronous JDBC Client for PostgreSQL 

This is my first experience using Vert.x and in Assynchronous programming. I am happy with the implementation but keep that in mind, in case something in here burns your eyes 😂

![Coverage](https://img.shields.io/badge/Coverage-89%25-green.svg)
![Build](https://img.shields.io/badge/Build-Passing-brightgreen.svg)
[![Vertx](https://img.shields.io/badge/vert.x-3.6.2-purple.svg)](link="https://vertx.io")


## Client Configuration
Change the config.json file in src/main/resources as follows:


	"url": "jdbc:postgresql://<DB_HOST>:<DB_PORT>/",
	"driver_class": "org.postgresql.Driver",
	"user": "<DB_USER>",
	"password": "<USER_PW>",
	"http.port" : <PORT_TO_SERVE_CLIENT>


## Build

From the project root, execute:

```
./gradlew build
```


##  Execution

From project root, execute:
```
java -jar build/libs/vertx-Postgres-client-1.0-SNAPSHOT-fat.jar -conf src/main/resources/config.json -worker
```

## Usage

Assuming execution on ```localhost:80```, the following methods are available:

```GET http://localhost:80/ ```-> Life-check method: to verify if the application is up and running.

```GET http://localhost:80/tables ``` -> returns all tables from all schemas 

```GET http://localhost:80/tables/:schema ```-> returns all tables from the specified schema 

```GET http://localhost:80/tables/:schema/:table ```-> returns more detailed information about the speficied table from the specified schema 

```GET http://localhost:80/tables/:schema/:table/structure ```-> returns detailed information about the columns for the specific table from the specified schema

```POST http://localhost:80/select/:schema/:table ```-> send a simple query to Postgres, over the table specified from the specified schema, and returns the query results.

Body of the request must be something like:
```
{
    "select": "column1, column2, column3",
    "where": "column1 = 'example1' or column2 = 'example2'"
}
```
```POST http://localhost:80/insert/:schema/:table ```-> send a insert statement to Postgres, for the table specified from the specified schema, and returns information on wether the statement was successful or not.

Body of the request must be something like (assuming column1 is ```String``` and column2 is ```boolean```):

```
{
    "columns": "column1,column2",
    "values": [
        [
            "String1ForColumn1”,
            true
        ],
        [
            "String2ForColumn1”,
            null
        ]
    ]
}
```

```POST http://localhost:80/delete/:schema/:table ```-> send a delete statement to Postgres, for the table specified from the specified schema, and returns information on wether the statement was successful or not.

Body of the request must be something like (assuming column1 is ```String```):


```
{
	"where": "column1 = 'Teste11'"
}
```

### Notes

If you created your tables via pgadmin application, their columns will have quotes (") as part of their names.
For this reason, you will need to include and escape those quotes on your JSON Request Bodies. For instance:

This:
```"where": "column1 = 'Teste11'"```

Must be provided as:

```"where": "\"column1\" = 'Teste11'"```

If you don't do this, nothing bad will happen to your table, you'll simply get the typical "column not found" error as response.

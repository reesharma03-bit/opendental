# Java API with Docker

A minimal Spring Boot Java REST API project with Docker support.

## Build

```bash
mvn package
```

## Run locally

```bash
mvn spring-boot:run
```

## Docker

Build image:

```bash
docker build -t java-api .
```

Run container:

```bash
docker run -p 8080:8080 java-api
```

## Available placeholder endpoints

- `GET /api/appointments`
- `GET /api/appointments/{aptNum}`
- `POST /api/appointments`
- `PUT /api/appointments/{aptNum}`
- `GET /api/patients`
- `GET /api/patients/Simple`
- `GET /api/patients/{patNum}`
- `POST /api/patients`
- `PUT /api/patients/{patNum}`
- `GET /api/documents`
- `GET /api/documents/{docNum}`
- `POST /api/documents/Upload`
- `POST /api/documents/UploadSftp`
- `POST /api/documents/DownloadSftp`
- `POST /api/documents/Thumbnails`
- `POST /api/documents/DownloadMount`
- `POST /api/documents/SetByUrl`
- `PUT /api/documents/{docNum}`
- `DELETE /api/documents/{docNum}`
- `POST /api/queries`
- `PUT /api/queries/ShortQuery`
- `GET /api/procedurelogs`
- `GET /api/procedurelogs/{procNum}`
- `POST /api/procedurelogs`
- `PUT /api/procedurelogs/{procNum}`
- `DELETE /api/procedurelogs/{procNum}`
- `GET /api/insurance`
- `GET /api/insurance/Simple`
- `GET /api/insurance/{insSubNum}`
- `POST /api/insurance`
- `PUT /api/insurance/{insSubNum}`
- `GET /api/comm`
- `GET /api/comm/{commNum}`
- `POST /api/comm`
- `PUT /api/comm/{commNum}`
- `DELETE /api/comm/{commNum}`
- `GET /api/readall`
- `GET /api/allothers`
- `GET /api/setup`
- `GET /api/enterprise`

## Notes

This project currently implements placeholder endpoints matching the requested Open Dental API categories. The API routes are ready for business logic and data integration.

# BeSalud - Memory Context

## Project Overview
BeSalud is a web-based medical clinic management system built with Java/Spring Boot/MongoDB. It manages patients, doctors, and medical appointments with role-based access (Admin, Doctor, Patient).

## Key Components
- **Core system**: Patient registration, appointment scheduling, medical dictámenes (reports)
- **Predictive module**: Prophet-based demand forecasting for appointments by specialty and doctor
- **Specialties**: Medicina General, Pediatría, Neumología, Cardiología, Dermatología
- **14 simulated doctors** with weekly schedules and one day off each

## Predictive Model (Prophet)
- **Target variable**: `volumen_citas_total` (daily appointment volume)
- **Components**: trend + weekly seasonality + annual seasonality + laborable day effect + rainfall effect
- **Cartagena-specific factors**: 
  - Rainy season multipliers (october: 3.0x, september: 2.0x, may-jun: 1.5x, nov: 2.5x)
  - Health alerts (IRA/dengue outbreaks: +30% Medicine General/Pediatría, +20% Neumología)
  - Colombian weekends/holidays: 0-2 citations/day drop
- **Evaluation**: Holdout validation (last 60 days), MAE ~4 citations/day for Medicine General
- **API**: FastAPI on Railway, consumed by Spring Boot via `API_PYTHON_URL` env var

## Data Structure
- `dataset_citas_medicas_cartagena.csv`: 5,095 rows, 37,382 citations daily per specialty
- `citas_por_medico.csv`: per-doctor daily citations
- Generated via `generar_citas.py`: 14 doctors, ~5,000 patients, SOAP dictámenes per citation

## Usage Notes
- Login passwords: `Demo1234` (all simulated accounts)
- Data simulation rules are realistic for Cartagena, Colombia
- Model trains per series (specialty or doctor), cached after first query
- Panel shows: projected demand, busiest day, total period, days without sufficient capacity
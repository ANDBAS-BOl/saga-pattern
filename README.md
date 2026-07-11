# Saga Pattern with Spring Boot & Apache Kafka

Proyecto de práctica cuyo objetivo es **aplicar conocimientos de Apache Kafka y del patrón Saga
(orquestación)** en un sistema de microservicios event-driven con Spring Boot.

Simula el flujo de una **orden de compra** distribuida entre varios servicios que se coordinan
**únicamente a través de eventos y comandos en Kafka** (sin llamadas síncronas entre ellos),
demostrando cómo mantener la consistencia de datos en un sistema distribuido **sin transacciones
ACID globales**, mediante **transacciones de compensación**.

> Repositorio con fines de aprendizaje y portafolio. Basado en el curso de saga orchestration de
> *appsdeveloperblog*, extendido con la implementación del **camino de compensación** (rechazo de
> orden y cancelación de reserva).

---

## 🎯 Qué demuestra este proyecto

- **Patrón Saga orquestado:** un orquestador central (`OrderSaga`) dirige el flujo enviando
  *commands* y reaccionando a *events*.
- **Transacciones de compensación:** ante un fallo (p. ej. pago rechazado), se deshacen
  semánticamente los pasos previos (cancelar reserva de producto → rechazar orden).
- **Comunicación event-driven** desacoplada sobre un cluster **Kafka de 3 brokers con KRaft**
  (sin ZooKeeper).
- **Arquitectura por capas** (dominio / servicio / infraestructura) con productores y consumidores
  Kafka como *adapters*.
- Buenas prácticas de mensajería: **productor idempotente**, `acks=all`, serialización JSON con
  *trusted packages* y ruteo por tipo con `@KafkaHandler`.

---

## 🏗️ Arquitectura y servicios

| Servicio | Puerto | Rol |
|----------|:------:|-----|
| `orders-service` | 8080 | Expone la API REST de órdenes y **contiene el orquestador de la Saga** (`OrderSaga`). |
| `products-service` | 8081 | Reserva y cancela reserva de producto (participa en la Saga). |
| `payments-service` | 8082 | Procesa el pago llamando al *credit card processor*; emite éxito o fallo. |
| `credit-card-processor-service` | 8084 | Servicio externo simulado de cobro. **Apagarlo fuerza el fallo del pago.** |
| `core` | — | Módulo compartido: DTOs de eventos, comandos y tipos comunes. |

Cada microservicio tiene su **propia base de datos** (H2 en memoria), reforzando el aislamiento
típico de microservicios.

### Topics de Kafka

| Topic | Contenido |
|-------|-----------|
| `orders-events` | Eventos de la orden (`OrderCreatedEvent`, `OrderApprovedEvent`). |
| `orders-commands` | Comandos hacia la orden (`ApproveOrderCommand`, `RejectOrderCommand`). |
| `products-commands` | Comandos hacia productos (`ReserveProductCommand`, `CancelProductReservationCommand`). |
| `products-events` | Eventos de productos (`ProductReservedEvent`, `ProductReservationCancelledEvent`). |
| `payments-commands` | Comandos hacia pagos (`ProcessPaymentCommand`). |
| `payments-events` | Eventos de pagos (`PaymentProcessedEvent`, `PaymentFailedEvent`). |

---

## 🔄 Flujo de la Saga

### Happy path (orden aprobada)

```
Cliente → POST /orders (orders-service)
  └─▶ OrderCreatedEvent ─────────────▶ Saga
        └─▶ ReserveProductCommand ───▶ products-service ─▶ ProductReservedEvent ─▶ Saga
              └─▶ ProcessPaymentCommand ─▶ payments-service ─▶ (cobra en CCP OK)
                    └─▶ PaymentProcessedEvent ─▶ Saga
                          └─▶ ApproveOrderCommand ─▶ orders-service ─▶ APPROVED
```

### Compensación (orden rechazada)

Si el pago falla (por ejemplo, **apagando el `credit-card-processor-service`**), la Saga revierte
los pasos ya ejecutados:

```
payments-service (CCP caído) ─▶ PaymentFailedEvent ─▶ Saga
  └─▶ CancelProductReservationCommand ─▶ products-service (repone stock)
        └─▶ ProductReservationCancelledEvent ─▶ Saga
              └─▶ RejectOrderCommand ─▶ orders-service ─▶ REJECTED
```

---

## 🛠️ Stack técnico

- **Java 21**
- **Spring Boot 3.2.5** (Web, Data JPA, Kafka)
- **Apache Kafka** (3 brokers, modo **KRaft**) vía Docker Compose
- **H2** (base de datos en memoria por servicio)
- **Gradle** (multi-módulo, con *wrapper* incluido)

---

## 🚀 Cómo levantar el proyecto

### Requisitos
- Docker y Docker Compose
- JDK 21

### 1. Levantar el cluster de Kafka (3 brokers KRaft)

```bash
docker compose up -d
```

Esto expone los brokers en `localhost:9092`, `localhost:9094` y `localhost:9096`
(los mismos que usan los servicios en su configuración).

### 2. Arrancar los microservicios

Cada servicio es una app Spring Boot independiente. Desde la raíz del proyecto:

```bash
./gradlew :credit-card-processor-service:bootRun   # puerto 8084
./gradlew :products-service:bootRun                # puerto 8081
./gradlew :payments-service:bootRun                # puerto 8082
./gradlew :orders-service:bootRun                  # puerto 8080
```

> En Windows usa `gradlew.bat` en lugar de `./gradlew`.

### 3. Probar el flujo

**Crear una orden** (happy path):

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"<uuid>","productId":"<uuid>","productQuantity":1}'
```

**Forzar la compensación:** apaga el `credit-card-processor-service` (Ctrl+C o
`docker`/proceso) antes de crear la orden. El pago fallará y la Saga terminará marcando la orden
como `REJECTED` tras cancelar la reserva del producto.

---

## 📁 Estructura del repositorio

```
saga-pattern/
├── core/                            # DTOs compartidos: eventos, comandos, tipos
├── orders-service/                  # API REST + orquestador de la Saga (OrderSaga)
├── products-service/                # Reserva / cancelación de productos
├── payments-service/                # Procesamiento de pagos + cliente del CCP
├── credit-card-processor-service/   # Servicio externo simulado de cobro
├── docker-compose.yml               # Cluster Kafka de 3 brokers (KRaft)
└── settings.gradle                  # Definición del proyecto multi-módulo
```

---

## 📝 Notas de diseño

- Los productores/consumidores Kafka viven en la capa de **infraestructura** como *adapters*; el
  dominio no conoce Kafka.
- El **orquestador** concentra la lógica del flujo, facilitando trazar y monitorear la Saga.
- La consistencia se logra con **transacciones de compensación**, no con transacciones ACID
  distribuidas (2PC), que no escalan en microservicios.

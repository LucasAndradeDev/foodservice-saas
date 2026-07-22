# 🍔 Food Service SaaS - MVP
## Sistema de Gestão de Mesas para Restaurantes

### Objetivo
Desenvolver um SaaS para restaurantes presenciais focado no gerenciamento de mesas, comandas e pedidos.

O objetivo do MVP é resolver apenas a operação do salão.

**Não fazem parte do MVP:**
- Delivery
- Estoque
- Fiscal
- QR Code
- Cardápio Digital
- Fidelidade
- Relatórios avançados
- Integrações

---

### Público-alvo
Pequenos e médios restaurantes que trabalham com atendimento em mesas.

**Exemplos:**
- Restaurantes
- Hamburguerias
- Pizzarias
- Churrascarias
- Bares
- Casas de sushi

---

### Arquitetura

#### Backend
- Java 21
- Spring Boot 3
- Spring Security
- JWT + Refresh Token
- Spring Data JPA
- PostgreSQL
- Flyway
- Docker

#### Frontend
- React
- TypeScript
- Vite
- React Query
- React Router
- TailwindCSS

#### Infraestrutura
- Docker Compose
- Nginx
- PostgreSQL

#### Arquitetura do Sistema
O sistema será **Multi-Tenant**.
- Cada restaurante possui seus próprios dados.
- Todos os registros possuem: `restaurant_id`
- Nunca haverá acesso entre restaurantes.

---

### Módulos

#### 1. Autenticação
**Funcionalidades:**
- Login
- Logout
- Refresh Token
- Alteração de senha
- Cadastro do restaurante
- Cadastro do proprietário

#### 2. Restaurante
- **Cadastro:** Nome, CNPJ, Telefone, Endereço
- **Configurações:** Nome fantasia, Logo, Cor principal, Quantidade de mesas

#### 3. Usuários
- **Perfis:** `OWNER`, `MANAGER`, `WAITER`, `KITCHEN`, `CASHIER`
- Cada perfil possui permissões específicas.

#### 4. Mesas
- **CRUD completo**
- **Campos:** `id`, `number`, `status`, `restaurant_id`, `active`
- **Status:** `FREE`, `OCCUPIED`, `CLOSING`

#### 5. Categorias
- **Campos:** `id`, `name`, `active`, `restaurant_id`
- **Exemplos:** Hambúrgueres, Bebidas, Sobremesas, Pizzas

#### 6. Produtos
- **Campos:** `id`, `name`, `description`, `price`, `active`, `category_id`, `restaurant_id`

#### 7. Comanda (Tab)
Representa a conta aberta da mesa.
- **Campos:** `id`, `table_id`, `status`, `opened_at`, `closed_at`, `restaurant_id`
- **Status:** `OPEN`, `CLOSED`
- **Regras:** Uma mesa só pode possuir uma comanda aberta.

#### 8. Pedido (Order)
Representa um envio para cozinha. Uma mesma comanda pode possuir vários pedidos.

**Exemplo:**
- Mesa 7
  - 19:00: 2 Hambúrgueres
  - 20:15: 1 Refrigerante
  - 21:00: 1 Sobremesa
  - *(São três pedidos diferentes.)*

- **Campos:** `id`, `tab_id`, `created_at`, `restaurant_id`

#### 9. Item do Pedido
- **Campos:** `id`, `order_id`, `product_id`, `quantity`, `unit_price`, `observation`, `status`
- **Status:** `PENDING`, `PREPARING`, `READY`, `DELIVERED`, `CANCELLED`
- O preço é salvo no momento da venda.

#### 10. Pagamento
Para o MVP, manter simples.
- **Campos:** `payment_method`, `paid_amount`, `paid_at` *(dentro da própria Comanda/Tab)*
- **Métodos:** `PIX`, `CASH`, `DEBIT_CARD`, `CREDIT_CARD`

---

### Regras de Negócio

#### Mesas
- Uma mesa livre pode abrir uma comanda.
- Ao abrir: `FREE` → `OCCUPIED`
- Ao fechar: `OCCUPIED` → `FREE`

#### Produtos
- Somente produtos ativos podem ser vendidos.

#### Comandas
- Uma mesa só possui uma comanda aberta.

#### Pedidos
- Um pedido nunca é apagado.

#### Itens
- Cada item possui seu próprio status.
- **Fluxo:** `PENDING` → `PREPARING` → `READY` → `DELIVERED` *(ou `CANCELLED`)*

#### Fechamento
- Uma comanda só pode ser fechada quando todos os itens estiverem `DELIVERED` ou `CANCELLED`.

---

### Fluxo Principal
```
Cliente chega
   ↓
Garçom abre mesa
   ↓
Sistema cria Comanda
   ↓
Garçom adiciona itens
   ↓
Sistema cria Pedido
   ↓
Cozinha recebe
   ↓
Cozinha prepara
   ↓
Garçom entrega
   ↓
Cliente pede conta
   ↓
Pagamento
   ↓
Comanda fecha
   ↓
Mesa volta para Livre
```

---

### Telas

#### Dashboard
- Mesas Livres
- Mesas Ocupadas
- Pedidos em preparo
- Faturamento do dia

#### Login
- Login / Senha

#### Mesas
- Grade: 🟢 Mesa 1 | 🔴 Mesa 2 | 🟢 Mesa 3 | 🟡 Mesa 4
- Ao clicar: Abre detalhes da mesa.

#### Mesa (Detalhes)
- **Mostrar:** Itens, Quantidade, Observação, Valor
- **Botões:** Adicionar Item, Enviar para Cozinha, Fechar Conta

#### Produtos
- CRUD
- Pesquisar
- Filtrar por categoria

#### Categorias
- CRUD

#### Cozinha
- Lista em tempo real (ex: Mesa 4 - Pedido 18 - 2 Hambúrguer Sem cebola)
- **Botões:** Recebido, Preparando, Pronto, Entregue

#### Caixa
- Mesa X, Itens, Subtotal, Total, Forma de pagamento, Fechar Conta

---

### Ordem de Desenvolvimento

- **Sprint 1:** Infraestrutura (Docker, PostgreSQL, Flyway, Spring Boot, React), Login (JWT), Multi-Tenant
- **Sprint 2:** Restaurante, Usuários, Permissões
- **Sprint 3:** Categorias, Produtos
- **Sprint 4:** Mesas (CRUD, Status)
- **Sprint 5:** Comandas (Abrir, Fechar)
- **Sprint 6:** Pedidos (Adicionar itens, Remover itens, Observações, Total)
- **Sprint 7:** Tela da Cozinha (Atualização dos status)
- **Sprint 8:** Pagamento, Fechamento, Dashboard

---

### Funcionalidades Futuras (Backlog)
Após validar o produto com clientes reais, considerar:
- Comanda por QR Code
- Cardápio digital
- Pedidos pelo celular
- Delivery
- Balcão
- Impressão de comandas
- Impressão de cozinha
- Impressão fiscal (NFC-e)
- Controle de estoque
- Ficha técnica
- Promoções e cupons
- Programa de fidelidade
- Integração com iFood
- Integração com WhatsApp
- Relatórios financeiros
- Dashboard analítico
- Aplicativo para garçons
- Aplicativo para clientes
- Multiunidade (redes de restaurantes)

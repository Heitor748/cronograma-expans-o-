# Cronograma Culto Expansão

Site (single-page, HTML/CSS/JS) para controlar o cronograma do Culto Expansão, com **login** e **sincronização em tempo real** via Supabase.

## Como usar

Abra `index.html` em qualquer navegador — não precisa de servidor nem build.
Para publicar: GitHub Pages, Netlify, Vercel ou qualquer host estático.

## Funcionalidades

- Login com e-mail e senha (Supabase Auth).
- Cronograma compartilhado entre todos os usuários logados.
- Cadastro de cultos com data, horário, tema, pregador, dirigente, observações.
- Estrutura padrão de momentos (Boas-vindas, Louvor, Avisos, Mensagem, Ministração) criada automaticamente em cada culto novo, editável depois.
- Adicionar, editar e remover momentos com horário e responsável.
- Filtro por mês e busca por texto.
- Modo claro/escuro.
- Sincronização em tempo real: edição num aparelho aparece nos outros em segundos.

## Setup do Supabase (feito uma vez)

### 1. Criar projeto
Em https://supabase.com. Guarde a senha do banco.

### 2. Rodar o SQL (SQL Editor → New query)

```sql
create table public.cultos (
  id uuid primary key default gen_random_uuid(),
  data date not null,
  hora text,
  tema text,
  pregador text,
  dirigente text,
  obs text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

create table public.momentos (
  id uuid primary key default gen_random_uuid(),
  culto_id uuid not null references public.cultos(id) on delete cascade,
  hora text,
  atividade text not null,
  responsavel text,
  ordem int default 0,
  created_at timestamptz default now()
);

create index momentos_culto_id_idx on public.momentos(culto_id);

alter table public.cultos  enable row level security;
alter table public.momentos enable row level security;

create policy "cultos_rw" on public.cultos
  for all to authenticated using (true) with check (true);

create policy "momentos_rw" on public.momentos
  for all to authenticated using (true) with check (true);

alter publication supabase_realtime add table public.cultos;
alter publication supabase_realtime add table public.momentos;
```

### 3. Auth
- Em **Authentication → Providers**, deixe `Email` ligado.
- Se não quiser que cada usuário precise confirmar o e-mail antes de entrar, desligue `Confirm email`.

### 4. Chaves no `index.html`
Na seção `<script type="module">` troque:
- `SUPABASE_URL` pela URL do seu projeto.
- `SUPABASE_KEY` pela sua `publishable` key (ou `anon public` legada).

## Como adicionar membros da equipe

1. Peça para cada líder acessar o site e clicar em **"Criar conta"** com e-mail e senha.
2. (Opcional) Em `Authentication → Users` no Supabase você vê e gerencia todos.
3. Para bloquear cadastros abertos: `Authentication → Providers → Email` → desligue `Allow new users to sign up`.

## Observações de segurança

A chave pública (`publishable` / `anon`) é segura no HTML. A proteção dos dados é via RLS (as policies acima permitem acesso só a quem está autenticado).

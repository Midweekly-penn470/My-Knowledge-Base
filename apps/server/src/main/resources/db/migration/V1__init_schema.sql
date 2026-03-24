create table user_accounts (
    id uuid primary key,
    username varchar(32) not null unique,
    email varchar(128) not null unique,
    password_hash varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table knowledge_bases (
    id uuid primary key,
    owner_id uuid not null references user_accounts(id),
    name varchar(80) not null,
    description varchar(500),
    visibility varchar(16) not null,
    dify_dataset_id varchar(128),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_knowledge_base_owner_name unique (owner_id, name)
);

create table knowledge_base_shares (
    id uuid primary key,
    knowledge_base_id uuid not null references knowledge_bases(id),
    shared_with_id uuid not null references user_accounts(id),
    access_role varchar(16) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_knowledge_base_share unique (knowledge_base_id, shared_with_id)
);

create index idx_knowledge_bases_owner_id on knowledge_bases(owner_id);
create index idx_knowledge_base_shares_shared_with_id on knowledge_base_shares(shared_with_id);

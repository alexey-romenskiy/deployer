-- noinspection SqlNoDataSourceInspectionForFile

create schema deployer;
set schema 'deployer';

create table deployer.sequence
(
    name    varchar not null,
    last_id bigint  not null,
    primary key (name)
);

create table deployer.user
(
    id                     bigint                   not null,
    username               varchar                  not null,
    last_activity_time     timestamp with time zone not null,
    email                  varchar null,
    can_login              boolean                  not null,
    can_register_artifact  boolean                  not null,
    can_request_deployment boolean                  not null,
    primary key (id),
    unique (username)
);

insert into deployer.sequence (name, last_id)
values ('user', 0);

create table deployer.registered_artifact
(
    id                       bigint                   not null,
    group_id                 varchar                  not null,
    artifact_id              varchar                  not null,
    classifier               varchar                  not null,
    extension                varchar                  not null,
    base_version             varchar                  not null,
    version                  varchar                  not null,
    snapshot                 boolean                  not null,
    url                      varchar                  not null,
    build_vcs_number         varchar null,
    teamcity_build_id        varchar null,
    teamcity_build_conf_name varchar null,
    teamcity_project_name    varchar null,
    user_id                  bigint                   not null,
    registration_time        timestamp with time zone not null,
    primary key (id),
    unique (group_id, artifact_id, classifier, extension, version),
    foreign key (user_id) references deployer.user (id)
);

insert into deployer.sequence (name, last_id)
values ('registered_artifact', 0);

create table deployer.signoff_group
(
    id           bigint  not null,
    all_required boolean not null,
    primary key (id)
);

insert into deployer.sequence (name, last_id)
values ('signoff_group', 0);

create table deployer.signoff_group_member_user
(
    signoff_group_id bigint not null,
    member_user_id   bigint not null,
    primary key (signoff_group_id, member_user_id),
    foreign key (signoff_group_id) references deployer.signoff_group (id),
    foreign key (member_user_id) references deployer.user (id)
);

create table deployer.signoff_group_member_group
(
    signoff_group_id bigint not null,
    member_group_id  bigint not null,
    primary key (signoff_group_id, member_group_id),
    foreign key (signoff_group_id) references deployer.signoff_group (id),
    foreign key (member_group_id) references deployer.signoff_group (id)
);

create table deployer.environment
(
    id                 bigint  not null,
    name               varchar not null,
    signoff_group_id   bigint  not null,
    last_deployment_id bigint null,
    primary key (id),
    unique (name),
    foreign key (signoff_group_id) references deployer.signoff_group (id)
);

insert into deployer.sequence (name, last_id)
values ('environment', 0);

create table deployer.artifact_deployment
(
    id                     bigint                   not null,
    registered_artifact_id bigint                   not null,
    environment_id         bigint                   not null,
    requestor_user_id      bigint                   not null,
    request_time           timestamp with time zone not null,
    last_edit_time         timestamp with time zone null,
    requested_asap         boolean null,
    requested_start_time   timestamp with time zone null,
    requested_end_time     timestamp with time zone null,
    approved               boolean                  not null,
    signoff_time           timestamp with time zone null,
    deployment_started     boolean                  not null,
    deployment_start_time  timestamp with time zone null,
    deployment_end_time    timestamp with time zone null,
    finished               boolean                  not null,
    successful             boolean                  not null,
    command_output         text null,
    command_exit_code      integer null,
    primary key (id),
    foreign key (registered_artifact_id) references deployer.registered_artifact (id),
    foreign key (environment_id) references deployer.environment (id),
    foreign key (requestor_user_id) references deployer.user (id)
);

alter table deployer.environment
    add foreign key (last_deployment_id) references deployer.artifact_deployment (id);

insert into deployer.sequence (name, last_id)
values ('artifact_deployment', 0);

create table deployer.artifact_deployment_signoff
(
    artifact_deployment_id bigint                   not null,
    user_id                bigint                   not null,
    signoff_time           timestamp with time zone not null,
    primary key (artifact_deployment_id, user_id),
    foreign key (artifact_deployment_id) references deployer.artifact_deployment (id),
    foreign key (user_id) references deployer.user (id)
);

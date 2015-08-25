set echo off
set verify off
set serveroutput on

variable task_name varchar2(100)
variable execution_name varchar2(100)
begin
	:task_name := 'SQL Tune Run Once for Stmt';
	:execution_name := 'Execution One';
	dbms_sqltune.drop_tuning_task(:task_name);
exception
when others then
	null;
end;
/

-- Create SQL Tuning Advisor Task and Execute it
declare
        ret varchar2(2000);
begin
        ret := dbms_sqltune.create_tuning_task(
                sql_text=>q'[PUT_YOUR_OWNER_SQL_HERE',
                task_name=>:task_name
        );
        dbms_sqltune.execute_tuning_task(:task_name,:execution_name);
end;
/
-- Get SQL Tuning Advisor Task Report
select dbms_sqltune.report_tuning_task(task_name=>:task_name,section=>'ALL',execution_name=>:execution_name)
from dual
/


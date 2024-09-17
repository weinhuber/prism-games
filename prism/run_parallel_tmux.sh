#!/bin/bash

# Define an array of commands
commands=(
#    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -prop 3 -const 'months=2,pbar=0.2' -exportstrat strat_two_investors_regular_2.dot -mainlog log_two_investors_regular_2.txt"
#    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -prop 3 -const 'months=4,pbar=0.2' -exportstrat strat_two_investors_regular_4.dot -mainlog log_two_investors_regular_4.txt"

# aloha
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 4 -const 'bcmax=4,D=0,q=1' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_fair_1.dot -mainlog log_aloha_corr_fair_1.txt"
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 4 -const 'bcmax=4,D=0,q=0.4' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_fair_04.dot -mainlog log_aloha_corr_fair_04.txt"
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 4 -const 'bcmax=4,D=0,q=0.6' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_fair_06.dot -mainlog log_aloha_corr_fair_06.txt"
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 4 -const 'bcmax=4,D=0,q=0.8' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_fair_08.dot -mainlog log_aloha_corr_fair_08.txt"
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 3 -const 'bcmax=4,D=0,q=1' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_social_1.dot -mainlog log_aloha_corr_social_1.txt"
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 3 -const 'bcmax=4,D=0,q=0.4' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_social_04.dot -mainlog log_aloha_corr_social_04.txt"
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 3 -const 'bcmax=4,D=0,q=0.6' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_social_06.dot -mainlog log_aloha_corr_social_06.txt"
    "./bin/prism ../prism-examples/experimental_results/aloha_backoff2.prism ../prism-examples/experimental_results/aloha2.props -prop 3 -const 'bcmax=4,D=0,q=0.8' -javamaxmem 10g -javastack 100m -exportstrat strat_aloha_corr_social_08.dot -mainlog log_aloha_corr_social_08.txt"
# robot coordiantion
    "./bin/prism ../prism-examples/csgs/robot_coordination/robot_coordination2.prism ../prism-examples/csgs/robot_coordination/robot_coordination2.props -const 'l=4,q=1' -javamaxmem 10g -javastack 100m -mainlog coordination_1.txt -exportstrat coordination_1.dot"
    "./bin/prism ../prism-examples/csgs/robot_coordination/robot_coordination2.prism ../prism-examples/csgs/robot_coordination/robot_coordination2.props -const 'l=4,q=0.2' -javamaxmem 10g -javastack 100m -mainlog coordination_02.txt -exportstrat coordination_02.dot"
    "./bin/prism ../prism-examples/csgs/robot_coordination/robot_coordination2.prism ../prism-examples/csgs/robot_coordination/robot_coordination2.props -const 'l=4,q=0.4' -javamaxmem 10g -javastack 100m -mainlog coordination_04.txt -exportstrat coordination_04.dot"
    "./bin/prism ../prism-examples/csgs/robot_coordination/robot_coordination2.prism ../prism-examples/csgs/robot_coordination/robot_coordination2.props -const 'l=4,q=0.6' -javamaxmem 10g -javastack 100m -mainlog coordination_06.txt -exportstrat coordination_06.dot"
    "./bin/prism ../prism-examples/csgs/robot_coordination/robot_coordination2.prism ../prism-examples/csgs/robot_coordination/robot_coordination2.props -const 'l=4,q=0.8' -javamaxmem 10g -javastack 100m -mainlog coordination_08.txt -exportstrat coordination_08.dot"
# two investors
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=2,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_2.dot -mainlog log_two_investors_regular_2.txt"
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=3,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_3.dot -mainlog log_two_investors_regular_3.txt"
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=4,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_4.dot -mainlog log_two_investors_regular_4.txt"
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=5,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_5.dot -mainlog log_two_investors_regular_5.txt"
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=6,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_6.dot -mainlog log_two_investors_regular_6.txt"
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=7,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_7.dot -mainlog log_two_investors_regular_7.txt"
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=8,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_8.dot -mainlog log_two_investors_regular_8.txt"
    "./bin/prism ../prism-examples/experimental_results/two_investors_nash.prism ../prism-examples/experimental_results/two_investors_nash.props -const 'months=9,pbar=0.2' -javamaxmem 10g -javastack 100m -exportstrat strat_two_investors_regular_9.dot -mainlog log_two_investors_regular_9.txt"
)

# Function to run a command in a new tmux session
run_in_tmux() {
    local cmd=$1
    local session_name=$2
    tmux new-session -d -s "$session_name" "$cmd"
}

# Loop over the commands and execute each in a tmux session
for i in "${!commands[@]}"; do
    session_name="session_$i"
    echo "Starting tmux session $session_name with command: ${commands[$i]}"
    run_in_tmux "${commands[$i]}" "$session_name"
done

echo "All commands have been started in separate tmux sessions."

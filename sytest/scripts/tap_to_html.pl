#!/usr/bin/perl
#
# Modified by akquinet GmbH on 18.04.2023
#
# Originally from https://github.com/matrix-org/sytest
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#
# See the License for the specific language governing permissions and
# limitations under the License.

# Write a summary of a TAP file in a HTML format

use strict;
use warnings FATAL => 'all';

use TAP::Parser;
use TAP::Formatter::HTML;
use TAP::Parser::Aggregator;

# Get tap results filename and build name from argv
my $tap_file = $ARGV[0];
my $out_file = $ARGV[1];

my $aggregator = TAP::Parser::Aggregator->new;
my $fmt = TAP::Formatter::HTML->new;

$fmt->really_quiet(1);
$fmt->prepare;
$fmt->output_file($out_file);

# read in source file
my $session;
my $parser = TAP::Parser->new({
    source => $tap_file,
    callbacks => {
        ALL => sub {
         $session->result( $_[0] );
        }
    },
});
$session = $fmt->open_test( $tap_file, $parser );
$parser->run;
# build an aggregator
$aggregator->add( $tap_file, $parser );
# this is needed because otherwise the formatter will complain, the timings will be wrong in the output due to this
$aggregator->start;
$aggregator->stop;

#output the formatted test
$fmt->summary($aggregator);

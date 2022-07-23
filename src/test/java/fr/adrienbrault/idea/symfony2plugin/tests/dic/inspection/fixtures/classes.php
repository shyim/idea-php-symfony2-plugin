<?php

namespace Symfony\Component\DependencyInjection
{
    interface ContainerInterface
    {
        public function get();
    };
}

namespace Foobar
{
    class Car
    {
        const FOOBAR = null;
    }

    class NamedArgument
    {
        public function __construct($foobar)
        {
        }
    }
}

namespace Symfony\Component\DependencyInjection\Attribute
{
    class Autowire {}
}
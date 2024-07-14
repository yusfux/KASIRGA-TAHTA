from abc import ABC, abstractmethod
from typing import List


class TestTemplate(ABC):
    """
    Abstract class for compiling code tests
    """

    @abstractmethod
    def instructions(self) -> List[List[str]]:
        """
        Performs any setup tasks before compilation.
        """
        pass

    @abstractmethod
    def pass_adr(self) -> int:
        """
        Test is passed if this address is reached.
        """
        pass

    @abstractmethod
    def fail_adr(self) -> int:
        """
        Test is failed if this address is reached.
        """
        pass

    @abstractmethod
    def timeout(self) -> int:
        """
        Max timeout duration for the test.
        """
        pass
